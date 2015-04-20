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

#ifndef APACHE_HTRACE_TEST_TEST_H
#define APACHE_HTRACE_TEST_TEST_H

#include <inttypes.h> /* for PRIdPTR */
#include <stdarg.h> /* for va_list */
#include <unistd.h> /* for size_t */

#define TEST_ERROR_EQ 0
#define TEST_ERROR_GE 1
#define TEST_ERROR_GT 2
#define TEST_ERROR_LE 3
#define TEST_ERROR_LT 4
#define TEST_ERROR_NE 5

/**
 * Fail with an error message.
 *
 * @param fmt       printf-style format string.
 * @param ...       printf-style arguments.
 */
void fail(const char *fmt, ...);

/**
 * Check for a test condition.
 *
 * @param expected  The string which is expected.
 * @param text      Some text that will be printed out if the test
 *                      condition fails.
 * @param ty        Comparison type.  See TEST_ERROR_* constants.
 * @param fmt       printf-style format string.
 * @param ap        printf-style arguments.
 *
 * @return      0 on success; 1 on failure.
 */
int vexpect(const char *expected, const char *text, int ty,
        const char *fmt, va_list ap);

/**
 * Check for a test condition.
 *
 * @param expected  The string which is expected.
 * @param text      Some text that will be printed out if the test
 *                      condition fails.
 * @param ty        Comparison type.  See TEST_ERROR_* constants.
 * @param fmt       printf-style format string.
 * @param ...       printf-style arguments.
 *
 * @return          0 on success; 1 on failure.
 */
int expect(const char *expected, const char *text, int ty,
        const char *fmt, ...) __attribute__((format(printf, 4, 5)));

/**
 * Allocate a zero-initialized region of memory, or die.
 *
 * @param len       The length
 *
 * @return          A pointer to a zero-initialized malloc'ed region.
 */
void *xcalloc(size_t len);

/**
 * Copy a string, or die.
 *
 * @param in        The string to copy
 *
 * @return          A dynamically allocated copy of the input string.
 */
char *xstrdup(const char *in);

/**
 * Dump a set of bytes to a buffer in hexadecimal form.
 *
 * @param in        The input.
 * @param buf_len   Length of the input buffer.
 * @param out       The buffer to dump to.
 * @param out_len   Length of the buffer to dump to.  If the output buffer is
 *                      not long enough, not all the output will be written.
 */
void hexdump(void *in, int in_len, char *buf, int buf_len);

#define TEST_ERROR_GET_LINE_HELPER2(x) #x
#define TEST_ERROR_GET_LINE_HELPER(x) TEST_ERROR_GET_LINE_HELPER2(x)
#define TEST_ERROR_LOCATION_TEXT __FILE__ " at line " \
    TEST_ERROR_GET_LINE_HELPER(__LINE__)

#define EXPECT(...) do { if (expect(__VA_ARGS__)) return 1; } while (0);

#define EXPECT_EQ(expected, fmt, ...) \
    EXPECT(expected, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
           fmt, __VA_ARGS__);

#define EXPECT_STR_EQ(expected, str) \
    EXPECT_EQ(expected, "%s", str)

#define EXPECT_GE(expected, fmt, ...) \
    EXPECT(expected, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_GE, \
           fmt, __VA_ARGS__);

#define EXPECT_GT(expected, fmt, ...) \
    EXPECT(expected, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_GT, \
           fmt, __VA_ARGS__);

#define EXPECT_LE(expected, fmt, ...) \
    EXPECT(expected, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_LE, \
           fmt, __VA_ARGS__);

#define EXPECT_LT(expected, fmt, ...) \
    EXPECT(expected, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_LT, \
           fmt, __VA_ARGS__);

#define EXPECT_NE(expected, fmt, ...) \
    EXPECT(expected, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_NE, \
           fmt, __VA_ARGS__);

#define COMMON_TEST__TO_STR(x) #x
#define COMMON_TEST__TO_STR2(x) COMMON_TEST__TO_STR(x)

#define EXPECT_INT_EQ(expected, x) do { \
  char expected_buf[16] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%d", expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, "%d", x); \
} while(0);

#define EXPECT_INT_GE(expected, x) do { \
  char expected_buf[16] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%d", expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_GE, "%d", x); \
} while(0);

#define EXPECT_INT_GT(expected, x) do { \
  char expected_buf[16] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%d", expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_GT, "%d", x); \
} while(0);

#define EXPECT_UINT64_EQ(expected, x) do { \
  char expected_buf[32] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%" PRIu64, expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
         "%" PRIu64, x); \
} while(0);

#define EXPECT_UINT64_GE(expected, x) do { \
  char expected_buf[32] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%" PRIu64, expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_GE, \
         "%" PRIu64, x); \
} while(0);

#define EXPECT_UINT64_GT(expected, x) do { \
  char expected_buf[32] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%" PRIu64, expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_GT, \
         "%" PRIu64, x); \
} while(0);

#define EXPECT_INT64_EQ(expected, x) do { \
  char expected_buf[32] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%" PRId64, expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
         "%" PRId64, x); \
} while(0);

#define EXPECT_UINTPTR_EQ(expected, x) do { \
  char expected_buf[32] = { 0 }; \
  snprintf(expected_buf, sizeof(expected_buf), "%" PRIuPTR, expected); \
  EXPECT(expected_buf, TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
         "%" PRIuPTR, x); \
} while(0);

#define EXPECT_INT_ZERO(x) \
    EXPECT("0", TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
           "%d", x);

#define EXPECT_TRUE(x) \
    EXPECT("1", TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
           "%d", (!!x));

#define EXPECT_FALSE(x) \
    EXPECT("0", TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
           "%d", (!!x));

#define EXPECT_NONNULL(x) \
    EXPECT("0", TEST_ERROR_LOCATION_TEXT, TEST_ERROR_NE, \
           "%"PRIuPTR, (uintptr_t)x);

#define EXPECT_NULL(x) \
    EXPECT("0", TEST_ERROR_LOCATION_TEXT, TEST_ERROR_EQ, \
           "%"PRIuPTR, (uintptr_t)x);
#endif

// vim: ts=4:sw=4:tw=79:et
