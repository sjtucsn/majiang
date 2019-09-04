package com.sjtudoit.majiang.utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.catalina.filters.RequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
public class LogFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestFilter.class);

    // 获取url上的请求参数
    private static Map<String, String[]> getRequestParamMap(HttpServletRequest request) {
        Map<String, String[]> paramMap = new HashMap<>();
        Enumeration paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = (String) paramNames.nextElement();

            String[] paramValues = request.getParameterValues(paramName);
            paramMap.put(paramName, paramValues);
        }

        return paramMap;
    }

    // 日志输出拦截器
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String method = httpServletRequest.getMethod();
        if ("GET".equals(method) || "POST".equals(method)) {
            //请求路径
            String path = httpServletRequest.getRequestURI();
            //请求体参数
            Map<String, String[]> paramMap = getRequestParamMap(httpServletRequest);
            // 请求开始时间
            Long startTime = System.currentTimeMillis();
            //Spring通过DispatchServlet处理请求
            chain.doFilter(httpServletRequest, httpServletResponse);
            //请求结束时间
            Long endTime = System.currentTimeMillis();
            String params = JSONObject.toJSONString(paramMap);
            log.info("{} {} {} {} {}ms", method, path, params, httpServletResponse.getStatus(), endTime - startTime);
        } else {
            chain.doFilter(httpServletRequest, httpServletResponse);
        }
    }

    public void init(FilterConfig filterConfig) {}
    public void destroy() {}
}
