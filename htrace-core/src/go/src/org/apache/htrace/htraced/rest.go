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
	"log"
	"net/http"
	"org/apache/htrace/common"
	"org/apache/htrace/conf"
	"strconv"
)

type serverInfoHandler struct {
}

func (handler *serverInfoHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	version := common.ServerInfo{Version: common.RELEASE_VERSION}
	buf, err := json.Marshal(&version)
	if err != nil {
		log.Printf("error marshalling ServerInfo: %s\n", err.Error())
		w.WriteHeader(http.StatusInternalServerError)
		return
	}
	w.Write(buf)
}

type dataStoreHandler struct {
	store *dataStore
}

func (hand *dataStoreHandler) getReqField64(fieldName string, w http.ResponseWriter,
	req *http.Request) (int64, bool) {
	str := req.FormValue(fieldName)
	if str == "" {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("No " + fieldName + " specified."))
		return -1, false
	}
	val, err := strconv.ParseInt(str, 10, 64)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("Error parsing " + fieldName + ": " + err.Error()))
		return -1, false
	}
	return val, true
}

func (hand *dataStoreHandler) getReqField32(fieldName string, w http.ResponseWriter,
	req *http.Request) (int32, bool) {
	str := req.FormValue(fieldName)
	if str == "" {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("No " + fieldName + " specified."))
		return -1, false
	}
	val, err := strconv.ParseInt(str, 10, 32)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("Error parsing " + fieldName + ": " + err.Error()))
		return -1, false
	}
	return int32(val), true
}

type findSidHandler struct {
	dataStoreHandler
}

func (hand *findSidHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	req.ParseForm()
	sid, ok := hand.getReqField64("sid", w, req)
	if !ok {
		return
	}
	span := hand.store.FindSpan(sid)
	if span == nil {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	w.Write(span.ToJson())
}

type findChildrenHandler struct {
	dataStoreHandler
}

func (hand *findChildrenHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	req.ParseForm()
	sid, ok := hand.getReqField64("sid", w, req)
	if !ok {
		return
	}
	var lim int32
	lim, ok = hand.getReqField32("lim", w, req)
	if !ok {
		return
	}
	children := hand.store.FindChildren(sid, lim)
	if len(children) == 0 {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	jbytes, err := json.Marshal(children)
	if err != nil {
		panic(err)
	}
	w.Write(jbytes)
}

func startRestServer(cnf *conf.Config, store *dataStore) {
	mux := http.NewServeMux()

	serverInfoH := &serverInfoHandler{}
	mux.Handle("/serverInfo", serverInfoH)

	findSidH := &findSidHandler{dataStoreHandler: dataStoreHandler{store: store}}
	mux.Handle("/findSid", findSidH)

	findChildrenH := &findChildrenHandler{dataStoreHandler: dataStoreHandler{store: store}}
	mux.Handle("/findChildren", findChildrenH)

	http.ListenAndServe(cnf.Get(conf.HTRACE_WEB_ADDRESS), mux)
	log.Println("Started REST server...")
}
