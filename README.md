# Dynamic Resource Inject Filter

A servlet filter that dynamically injects CSS, JavaScript, and HTML fragments into HTML responses before they are sent to the client.

## Features

- Buffers downstream HTML safely (writer **or** output stream, never both)
- Skips non-`text/html` responses and forwards the original body unchanged
- Resolves injectable files from `servletPath` + `pathInfo` (wildcard mappings)
- Blocks path traversal under the configured resource root
- Optional response size limit to avoid unbounded memory use

- Ant-style URL exclusions to bypass the filter for downloads and other non-HTML endpoints
- Optional flag to disable the buffer size limit

## Resource layout

```
<resources>/
├── home.css
├── home.js
├── home.html
├── about.css
├── about.js
└── about.html
```

For request `/home` or `/app/home` (mapping dependent), the filter looks for `home.css`, `home.js`, and `home.html` under the resource root.

Examples:
| Request URL | Files loaded |
|-------------|--------------|
| `http://host:8080/login.jsp` | `login.css`, `login.js`, `login.html` |
| `http://host:8080/` | `.css`, `.js`, `.html` |

## `web.xml` configuration

```xml
<filter>
    <filter-name>DynamicResourceInjectFilter</filter-name>
    <filter-class>io.github.connellite.injector.DynamicResourceInjectFilter</filter-class>
    <init-param>
        <param-name>resources</param-name>
        <param-value>${user.dir}/resources</param-value>
    </init-param>
    <!-- Optional: default 5242880 (5 MiB) -->
    <init-param>
        <param-name>maxBufferBytes</param-name>
        <param-value>5242880</param-value>
    </init-param>
    <!-- Optional: skip buffering/injection for matching paths (Spring Ant-style) -->
    <init-param>
        <param-name>excludeUrlPatterns</param-name>
        <param-value>/Download/**</param-value>
    </init-param>
    <!-- Optional: disable response size limit (default false) -->
    <init-param>
        <param-name>bufferLimitDisabled</param-name>
        <param-value>false</param-value>
    </init-param>
</filter>

<filter-mapping>
    <filter-name>DynamicResourceInjectFilter</filter-name>
    <url-pattern>/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</filter-mapping>
```

### Init parameters

| Name | Required | Description |
|------|----------|-------------|
| `resources` | yes | Directory with `.css` / `.js` / `.html` fragments |
| `maxBufferBytes` | no | Max buffered response size (default `5242880`, 5 MiB). Parsed by the filter and passed to `CharResponseWrapper` as `maxBufferChars`. |
| `excludeUrlPatterns` | no | Comma- or space-separated Ant-style patterns. Matching requests bypass the filter entirely (no buffering, no injection). Based on [Spring AntPathMatcher](https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/main/java/org/springframework/util/AntPathMatcher.java). |
| `bufferLimitDisabled` | no | When `true`, disables the response buffer size limit (default `false`). Ignores `maxBufferBytes`. Use with care on large responses. |

### Buffer limit: `maxBufferBytes` and `maxBufferChars`

- **`maxBufferBytes`** — init parameter in `web.xml` (bytes).
- **`maxBufferChars`** — internal limit in `CharResponseWrapper`; the filter passes the parsed `maxBufferBytes` value into the wrapper under this name.

How the limit is applied:

| Output channel | Class | Unit |
|----------------|-------|------|
| `getWriter()` | `BoundedCharArrayWriter` | characters |
| `getOutputStream()` | `BufferedServletOutputStream` | bytes |

Both channels share the same numeric limit from `maxBufferBytes`. When `bufferLimitDisabled=true`, the wrapper uses unlimited buffering (`UNLIMITED_BUFFER_CHARS`).

### Exclude pattern examples

| Pattern | Matches | Does not match |
|---------|---------|----------------|
| `/Download/**` | `/Download`, `/Download/file.pdf` | `/Downloads/file.pdf` |
| `/Download/*` | `/Download/file.pdf` | `/Download` |
| `/*.jsp` | `/login.jsp` | `/app/login.jsp` |
| `/**/*.jsp` | `/login.jsp`, `/app/login.jsp` | `/login.html` |
| `/health` | `/health` only | `/health/check` |



For file downloads and other large binary responses, prefer `excludeUrlPatterns` over increasing `maxBufferBytes` or setting `bufferLimitDisabled=true`.



## Requirements

- Java 17+ on the server
- Read access to the resource directory
- Downstream servlets must write through the wrapped response (normal for filters)

## Build

| Profile | Servlet API |
|---------|-------------|
| `javax` (default) | `javax.servlet` 4.0 |
| `jakarta` | `jakarta.servlet` 6.0 |

```bash
mvn clean package
mvn clean package -Pjakarta
```

Servlet imports are selected at compile time with the [Manifold preprocessor](https://github.com/manifold-systems/manifold/tree/master/manifold-deps-parent/manifold-preprocessor) (`-AJAKARTA` for the `jakarta` profile).
