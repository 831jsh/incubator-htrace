/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "core/span.h"
#include "receiver/receiver.h"
#include "sampler/sampler.h"
#include "util/log.h"
#include "util/rand.h"
#include "util/string.h"
#include "util/time.h"

#include <inttypes.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/**
 * @file span.c
 *
 * Implementation of HTrace spans.
 */

struct htrace_span *htrace_span_alloc(const char *desc,
                uint64_t begin_ms, uint64_t span_id)
{
    struct htrace_span *span;

    span = malloc(sizeof(*span));
    if (!span) {
        return NULL;
    }
    span->desc = strdup(desc);
    if (!span->desc) {
        free(span);
        return NULL;
    }
    span->begin_ms = begin_ms;
    span->end_ms = 0;
    span->span_id = span_id;
    span->prid = NULL;
    span->num_parents = 0;
    span->parent.single = 0;
    span->parent.list = NULL;
    return span;
}

void htrace_span_free(struct htrace_span *span)
{
    if (!span) {
        return;
    }
    free(span->desc);
    free(span->prid);
    if (span->num_parents > 1) {
        free(span->parent.list);
    }
    free(span);
}

static int compare_spanids(const void *va, const void *vb)
{
    uint64_t a = *((uint64_t*)va);
    uint64_t b = *((uint64_t*)vb);
    if (a < b) {
        return -1;
    } else if (a > b) {
        return 1;
    } else {
        return 0;
    }
}

void htrace_span_sort_and_dedupe_parents(struct htrace_span *span)
{
    int i, j, num_parents = span->num_parents;
    uint64_t prev;

    if (num_parents <= 1) {
        return;
    }
    qsort(span->parent.list, num_parents, sizeof(uint64_t), compare_spanids);
    prev = span->parent.list[0];
    j = 1;
    for (i = 1; i < num_parents; i++) {
        uint64_t id = span->parent.list[i];
        if (id != prev) {
            span->parent.list[j++] = span->parent.list[i];
            prev = id;
        }
    }
    span->num_parents = j;
    if (j == 1) {
        // After deduplication, there is now only one entry.  Switch to the
        // optimized no-malloc representation for 1 entry.
        free(span->parent.list);
        span->parent.single = prev;
    } else if (j != num_parents) {
        // After deduplication, there are now fewer entries.  Use realloc to
        // shrink the size of our dynamic allocation if possible.
        uint64_t *nlist = realloc(span->parent.list, sizeof(uint64_t) * j);
        if (nlist) {
            span->parent.list = nlist;
        }
    }
}

/**
 * Translate the span to a JSON string.
 *
 * This function can be called in two ways.  With buf == NULL, we will determine
 * the size of the buffer that would be required to hold a JSON string
 * containing the span contents.  With buf non-NULL, we will write the span
 * contents to the provided buffer.
 *
 * @param scope             The scope
 * @param max               The maximum number of bytes to write to buf.
 * @param buf               If non-NULL, where the string will be written.
 *
 * @return                  The number of bytes that the span json would take
 *                          up if it were written out.
 */
static int span_json_sprintf_impl(const struct htrace_span *span,
                                  int max, char *buf)
{
    int num_parents, i, ret = 0;
    const char *prefix = "";

    // Note that we have validated the description and process ID strings to
    // make sure they don't contain anything evil.  So we don't need to escape
    // them here.

    ret += fwdprintf(&buf, &max, "{\"s\":\"%016" PRIx64 "\",\"b\":%" PRId64
                 ",\"e\":%" PRId64",", span->span_id, span->begin_ms,
                 span->end_ms);
    if (span->desc) {
        ret += fwdprintf(&buf, &max, "\"d\":\"%s\",", span->desc);
    }
    if (span->prid) {
        ret += fwdprintf(&buf, &max, "\"r\":\"%s\",", span->prid);
    }
    num_parents = span->num_parents;
    if (num_parents == 0) {
        ret += fwdprintf(&buf, &max, "\"p\":[]");
    } else if (num_parents == 1) {
        ret += fwdprintf(&buf, &max, "\"p\":[\"%016"PRIx64"\"]",
                         span->parent.single);
    } else if (num_parents > 1) {
        ret += fwdprintf(&buf, &max, "\"p\":[");
        for (i = 0; i < num_parents; i++) {
            ret += fwdprintf(&buf, &max, "%s\"%016" PRIx64 "\"", prefix,
                             span->parent.list[i]);
            prefix = ",";
        }
        ret += fwdprintf(&buf, &max, "]");
    }
    ret += fwdprintf(&buf, &max, "}");
    // Add one to 'ret' to take into account the terminating null that we
    // need to write.
    return ret + 1;
}

int span_json_size(const struct htrace_span *scope)
{
    return span_json_sprintf_impl(scope, 0, NULL);
}

void span_json_sprintf(const struct htrace_span *scope, int max, void *buf)
{
    span_json_sprintf_impl(scope, max, buf);
}

// vim:ts=4:sw=4:et
