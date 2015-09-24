/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package client

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"org/apache/htrace/common"
	"org/apache/htrace/conf"
)

// A golang client for htraced.
// TODO: fancier APIs for streaming spans in the background, optimize TCP stuff

func NewClient(cnf *conf.Config) (*Client, error) {
	hcl := Client{}
	hcl.restAddr = cnf.Get(conf.HTRACE_WEB_ADDRESS)
	hcl.hrpcAddr = cnf.Get(conf.HTRACE_HRPC_ADDRESS)
	return &hcl, nil
}

type Client struct {
	// REST address of the htraced server.
	restAddr string

	// HRPC address of the htraced server.
	hrpcAddr string

	// The HRPC client, or null if it is not enabled.
	hcr *hClient
}

// Get the htraced server information.
func (hcl *Client) GetServerInfo() (*common.ServerInfo, error) {
	buf, _, err := hcl.makeGetRequest("server/info")
	if err != nil {
		return nil, err
	}
	var info common.ServerInfo
	err = json.Unmarshal(buf, &info)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Error: error unmarshalling response "+
			"body %s: %s", string(buf), err.Error()))
	}
	return &info, nil
}

// Get the htraced server statistics.
func (hcl *Client) GetServerStats() (*common.ServerStats, error) {
	buf, _, err := hcl.makeGetRequest("server/stats")
	if err != nil {
		return nil, err
	}
	var stats common.ServerStats
	err = json.Unmarshal(buf, &stats)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Error: error unmarshalling response "+
			"body %s: %s", string(buf), err.Error()))
	}
	return &stats, nil
}

// Get information about a trace span.  Returns nil, nil if the span was not found.
func (hcl *Client) FindSpan(sid common.SpanId) (*common.Span, error) {
	buf, rc, err := hcl.makeGetRequest(fmt.Sprintf("span/%s", sid.String()))
	if err != nil {
		if rc == http.StatusNoContent {
			return nil, nil
		}
		return nil, err
	}
	var span common.Span
	err = json.Unmarshal(buf, &span)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Error unmarshalling response "+
			"body %s: %s", string(buf), err.Error()))
	}
	return &span, nil
}

func (hcl *Client) WriteSpans(req *common.WriteSpansReq) error {
	if hcl.hrpcAddr == "" {
		return hcl.writeSpansHttp(req)
	}
	hcr, err := newHClient(hcl.hrpcAddr)
	if err != nil {
		return err
	}
	defer hcr.Close()
	return hcr.writeSpans(req)
}

func (hcl *Client) writeSpansHttp(req *common.WriteSpansReq) error {
	var w bytes.Buffer
	var err error
	for i := range req.Spans {
		var buf []byte
		buf, err = json.Marshal(req.Spans[i])
		if err != nil {
			return errors.New(fmt.Sprintf("Error serializing span: %s",
				err.Error()))
		}
		_, err = w.Write(buf)
		if err != nil {
			return errors.New(fmt.Sprintf("Error writing span: %s",
				err.Error()))
		}
		_, err = w.Write([]byte{'\n'})
		//err = io.WriteString(&w, "\n")
		if err != nil {
			return errors.New(fmt.Sprintf("Error writing: %s",
				err.Error()))
		}
	}
	customHeaders := make(map[string]string)
	if req.DefaultTrid != "" {
		customHeaders["htrace-trid"] = req.DefaultTrid
	}
	_, _, err = hcl.makeRestRequest("POST", "writeSpans",
		&w, customHeaders)
	if err != nil {
		return err
	}
	return nil
}

// Find the child IDs of a given span ID.
func (hcl *Client) FindChildren(sid common.SpanId, lim int) ([]common.SpanId, error) {
	buf, _, err := hcl.makeGetRequest(fmt.Sprintf("span/%s/children?lim=%d",
		sid.String(), lim))
	if err != nil {
		return nil, err
	}
	var spanIds []common.SpanId
	err = json.Unmarshal(buf, &spanIds)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Error: error unmarshalling response "+
			"body %s: %s", string(buf), err.Error()))
	}
	return spanIds, nil
}

// Make a query
func (hcl *Client) Query(query *common.Query) ([]common.Span, error) {
	in, err := json.Marshal(query)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Error marshalling query: %s", err.Error()))
	}
	var out []byte
	var url = fmt.Sprintf("query?query=%s", in)
	out, _, err = hcl.makeGetRequest(url)
	if err != nil {
		return nil, err
	}
	var spans []common.Span
	err = json.Unmarshal(out, &spans)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Error unmarshalling results: %s", err.Error()))
	}
	return spans, nil
}

var EMPTY = make(map[string]string)

func (hcl *Client) makeGetRequest(reqName string) ([]byte, int, error) {
	return hcl.makeRestRequest("GET", reqName, nil, EMPTY)
}

// Make a general JSON REST request.
// Returns the request body, the response code, and the error.
// Note: if the response code is non-zero, the error will also be non-zero.
func (hcl *Client) makeRestRequest(reqType string, reqName string, reqBody io.Reader,
	customHeaders map[string]string) ([]byte, int, error) {
	url := fmt.Sprintf("http://%s/%s",
		hcl.restAddr, reqName)
	req, err := http.NewRequest(reqType, url, reqBody)
	req.Header.Set("Content-Type", "application/json")
	for k, v := range customHeaders {
		req.Header.Set(k, v)
	}
	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, -1, errors.New(fmt.Sprintf("Error: error making http request to %s: %s\n", url,
			err.Error()))
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, resp.StatusCode,
			errors.New(fmt.Sprintf("Error: got bad response status from %s: %s\n", url, resp.Status))
	}
	var body []byte
	body, err = ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, -1, errors.New(fmt.Sprintf("Error: error reading response body: %s\n", err.Error()))
	}
	return body, 0, nil
}

// Dump all spans from the htraced daemon.
func (hcl *Client) DumpAll(lim int, out chan *common.Span) error {
	defer func() {
		close(out)
	}()
	searchId := common.INVALID_SPAN_ID
	for {
		q := common.Query{
			Lim: lim,
			Predicates: []common.Predicate{
				common.Predicate{
					Op:    "ge",
					Field: "spanid",
					Val:   searchId.String(),
				},
			},
		}
		spans, err := hcl.Query(&q)
		if err != nil {
			return errors.New(fmt.Sprintf("Error querying spans with IDs at or after "+
				"%s: %s", searchId.String(), err.Error()))
		}
		if len(spans) == 0 {
			return nil
		}
		for i := range spans {
			out <- &spans[i]
		}
		searchId = spans[len(spans)-1].Id.Next()
	}
}

func (hcl *Client) Close() {
	if hcl.hcr != nil {
		hcl.hcr.Close()
	}
	hcl.restAddr = ""
	hcl.hcr = nil
}
