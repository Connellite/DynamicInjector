# Dynamic Resource Inject Filter

A servlet filter that dynamically injects CSS, JavaScript, and HTML fragments into HTML responses before they are sent to the client.

## Features

- Buffers downstream HTML safely (writer **or** output stream, never both)
- Skips non-`text/html` responses and forwards the original body unchanged
- Resolves injectable files from `servletPath` + `pathInfo` (wildcard mappings)
- Blocks path traversal under the configured resource root
- Optional response size limit to avoid unbounded memory use

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
| `maxBufferBytes` | no | Max buffered response size (default `5242880`) |

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
