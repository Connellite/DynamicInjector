# Dynamic Resource Inject Filter

A servlet filter that dynamically injects CSS, JavaScript, and HTML fragments into HTML responses before they are sent to the client.

## Features

The filter:

- Intercepts the HTML response
- Resolves matching resources (CSS, JS, HTML) from the request path
- Injects them into the response
- Sends the modified HTML to the client

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

## `web.xml` configuration

Example configuration:

```xml
<filter>
    <filter-name>DynamicResourceInjectFilter</filter-name>
    <filter-class>io.github.connellite.injector.DynamicResourceInjectFilter</filter-class>
    <init-param>
        <param-name>resources</param-name>
        <param-value>${user.dir}/resources</param-value>
    </init-param>
</filter>

<filter-mapping>
    <filter-name>DynamicResourceInjectFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

## Requirements

- The configured directory must exist
- Tomcat/Jetty must have read access to the resource files
- The response content must be HTML

## Build

JDK 17 is required. Servlet API flavor is selected with a Maven profile:

| Profile | Servlet API | Output JAR | Use with |
|---------|-------------|------------|----------|
| `javax` (default) | `javax.servlet` 4.0 | `dynamic-injector-1.0.jar` | Tomcat 9 |
| `jakarta` | `jakarta.servlet` 6.0 | `dynamic-injector-1.0-jakarta.jar` | Tomcat 10.1+, Tomcat 11 |

```bash
mvn clean package
mvn clean package -Pjakarta
```

Servlet imports are switched at compile time with the [Manifold preprocessor](https://github.com/manifold-systems/manifold/tree/master/manifold-deps-parent/manifold-preprocessor) (`-AJAKARTA` for the `jakarta` profile). For IDE support, install the [Manifold plugin](https://plugins.jetbrains.com/plugin/10057-manifold-ij) and enable the matching profile or define the `JAKARTA` symbol in the preprocessor settings.
