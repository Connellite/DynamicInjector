# Dynamic Resource Inject Filter

A servlet filter that dynamically injects CSS, JavaScript, and HTML fragments into HTML responses before they are sent to the client.

## Features

- Buffers downstream HTML safely (writer **or** output stream, never both)
- Streams non-`text/html` file responses directly once `Content-Type` is set
- Falls back to buffering for non-HTML responses without a `Content-Type`
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
        <param-name>maxBufferSize</param-name>
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
| `maxBufferSize` | no | Max buffered **HTML** size (default `5242880`, 5 MiB). |
| `excludeUrlPatterns` | no | Comma- or space-separated Ant-style patterns. Matching requests bypass the filter entirely (no buffering, no injection). Based on [Spring AntPathMatcher](https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/main/java/org/springframework/util/AntPathMatcher.java). |
| `bufferLimitDisabled` | no | When `true`, disables the HTML buffer size limit (default `false`). Ignores `maxBufferSize`. Use with care on large HTML responses. |

### Buffer limit: `maxBufferSize`

- Applies to **HTML buffering** via `getWriter()` or `getOutputStream()` after `Content-Type: text/html`.
- **File/binary** responses stream when `Content-Type` is non-HTML, or when buffered volume exceeds the limit while the type is still unknown.
- `getWriter()` always buffers (injection and text APIs use characters); non-HTML writer responses cannot stream.

| Output channel | When | Behaviour |
|----------------|------|-----------|
| `getOutputStream()` + file type | `Content-Type` set before/at write | passthrough |
| `getOutputStream()` + unknown type | exceeds `maxBufferSize` | auto passthrough |
| `getOutputStream()` + `text/html` | always | buffered |
| `getWriter()` | always | buffered |

### File downloads

When the downstream servlet sets a non-`text/html` `Content-Type` (for example `application/octet-stream`) before writing the body, the filter streams bytes directly to the client without buffering the whole file. `excludeUrlPatterns` remains useful to skip the filter entirely on known download endpoints.

### Exclude pattern examples

| Pattern | Matches | Does not match |
|---------|---------|----------------|
| `/Download/**` | `/Download`, `/Download/file.pdf` | `/Downloads/file.pdf` |
| `/Download/*` | `/Download/file.pdf` | `/Download` |
| `/*.jsp` | `/login.jsp` | `/app/login.jsp` |
| `/**/*.jsp` | `/login.jsp`, `/app/login.jsp` | `/login.html` |
| `/health` | `/health` only | `/health/check` |



For file downloads, prefer streaming via `getOutputStream()` and a non-HTML `Content-Type`. Use `excludeUrlPatterns` only when the filter should be skipped entirely.



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

Servlet imports are selected at compile time with the [Manifold preprocessor](https://github.com/manifold-systems/manifold/tree/master/manifold-deps-parent/manifold-preprocessor)
(`JAVAX` / `JAKARTA` in `build.properties`).
