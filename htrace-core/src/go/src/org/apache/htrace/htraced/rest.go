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

package main

import (
	"encoding/json"
	"fmt"
	"github.com/gorilla/mux"
	"io"
	"log"
	"mime"
	"net/http"
	"org/apache/htrace/common"
	"org/apache/htrace/conf"
	"org/apache/htrace/resource"
	"path/filepath"
	"strconv"
	"strings"
)

// Set the response headers.
func setResponseHeaders(hdr http.Header) {
	hdr.Set("Content-Type", "application/json")
}

// Write a JSON error response.
func writeError(w http.ResponseWriter, errCode int, fstr string, args ...interface{}) {
	str := fmt.Sprintf(fstr, args)
	str = strings.Replace(str, `"`, `'`, -1)
	log.Println(str)
	w.WriteHeader(errCode)
	w.Write([]byte(`{ "error" : "` + str + `"}`))
}

type serverInfoHandler struct {
}

func (handler *serverInfoHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	setResponseHeaders(w.Header())
	version := common.ServerInfo{ReleaseVersion: RELEASE_VERSION,
		GitVersion: GIT_VERSION}
	buf, err := json.Marshal(&version)
	if err != nil {
		writeError(w, http.StatusInternalServerError,
			"error marshalling ServerInfo: %s\n", err.Error())
		return
	}
	w.Write(buf)
}

type dataStoreHandler struct {
	store *dataStore
}

func (hand *dataStoreHandler) parse64(w http.ResponseWriter, str string) (int64, bool) {
	val, err := strconv.ParseUint(str, 16, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Failed to parse span ID %s: %s",
			str, err.Error())
		w.Write([]byte("Error parsing : " + err.Error()))
		return -1, false
	}
	return int64(val), true
}

func (hand *dataStoreHandler) getReqField32(fieldName string, w http.ResponseWriter,
	req *http.Request) (int32, bool) {
	str := req.FormValue(fieldName)
	if str == "" {
		writeError(w, http.StatusBadRequest, "No %s specified.", fieldName)
		return -1, false
	}
	val, err := strconv.ParseUint(str, 16, 32)
	if err != nil {
		writeError(w, http.StatusBadRequest, "Error parsing %s: %s.", fieldName, err.Error())
		return -1, false
	}
	return int32(val), true
}

type findSidHandler struct {
	dataStoreHandler
}

func (hand *findSidHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	setResponseHeaders(w.Header())
	req.ParseForm()
	vars := mux.Vars(req)
	stringSid := vars["id"]
	sid, ok := hand.parse64(w, stringSid)
	if !ok {
		return
	}
	span := hand.store.FindSpan(sid)
	if span == nil {
		writeError(w, http.StatusNoContent, "No spans were specified.")
		return
	}
	w.Write(span.ToJson())
}

type findChildrenHandler struct {
	dataStoreHandler
}

func (hand *findChildrenHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	setResponseHeaders(w.Header())
	req.ParseForm()
	vars := mux.Vars(req)
	stringSid := vars["id"]
	sid, ok := hand.parse64(w, stringSid)
	if !ok {
		return
	}
	var lim int32
	lim, ok = hand.getReqField32("lim", w, req)
	if !ok {
		return
	}
	children := hand.store.FindChildren(sid, lim)
	jbytes, err := json.Marshal(children)
	if err != nil {
		panic(err)
	}
	w.Write(jbytes)
}

type writeSpansHandler struct {
	dataStoreHandler
}

func (hand *writeSpansHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	setResponseHeaders(w.Header())
	dec := json.NewDecoder(req.Body)
	spans := make([]*common.Span, 0, 32)
	for {
		var span common.Span
		err := dec.Decode(&span)
		if err != nil {
			if err != io.EOF {
				writeError(w, http.StatusBadRequest, "Error parsing spans: %s", err.Error())
				return
			}
			break
		}
		spans = append(spans, &span)
	}
	for spanIdx := range spans {
		log.Printf("writing span %s\n", spans[spanIdx].ToJson())
		hand.store.WriteSpan(spans[spanIdx])
	}
}

type defaultServeHandler struct {
}

func (hand *defaultServeHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	ident := strings.TrimLeft(req.URL.Path, "/")
	if ident == "" {
		ident = "index.html" // default to index.html
	}
	ident = strings.Replace(ident, "/", "__", -1)
	rsc := resource.Catalog[ident]
	if rsc == "" {
		log.Printf("failed to find entry for %s\n", ident)
		w.WriteHeader(http.StatusNotFound)
		return
	}
	file_ext := filepath.Ext(req.URL.Path)
	mime_type := mime.TypeByExtension(file_ext)
	w.Header().Set("Content-Type", mime_type)
	w.Write([]byte(rsc))
}

func startRestServer(cnf *conf.Config, store *dataStore) {

	r := mux.NewRouter().StrictSlash(false)
	// Default Handler. This will serve requests for static requests.
	r.Handle("/", &defaultServeHandler{})

	r.Handle("/server/info", &serverInfoHandler{}).Methods("GET")

	writeSpansH := &writeSpansHandler{dataStoreHandler: dataStoreHandler{store: store}}
	r.Handle("/writeSpans", writeSpansH).Methods("POST")

	span := r.PathPrefix("/span").Subrouter()
	findSidH := &findSidHandler{dataStoreHandler: dataStoreHandler{store: store}}
	span.Handle("/{id}", findSidH).Methods("GET")

	findChildrenH := &findChildrenHandler{dataStoreHandler: dataStoreHandler{store: store}}
	span.Handle("/{id}/children", findChildrenH).Methods("GET")

	http.ListenAndServe(cnf.Get(conf.HTRACE_WEB_ADDRESS), r)
	log.Println("Started REST server...")
}
