package org.qiyu.live.gateway.filter;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.account.interfaces.IAccountTokenRPC;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.common.interfaces.enums.GatewayHeaderEnum;
import org.qiyu.live.gateway.properties.GatewayApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.netty.handler.codec.http.cookie.CookieHeaderNames.MAX_AGE;
import static org.springframework.web.cors.CorsConfiguration.ALL;

/**
 * @Author idea
 * @Date: Created in 10:57 2023/6/20
 * @Description
 */
@Component
public class AccountCheckFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountCheckFilter.class);

    @DubboReference(check = false)
    private IAccountTokenRPC accountTokenRPC;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private GatewayApplicationProperties gatewayApplicationProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取请求url，判断是否为空，如果为空则返回请求不通过
        ServerHttpRequest request = exchange.getRequest();
        String reqUrl = request.getURI().getPath();
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://web.qiyu.live.com:5500");
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST, GET");
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, ALL);
        headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE);

        if (StringUtils.isEmpty(reqUrl)) {
            return Mono.empty();
        }
        //根据url，判断是否存在于url白名单中，如果存在，则不对token进行校验
        List<String> notCheckUrlList = gatewayApplicationProperties.getNotCheckUrlList();
        for (String notCheckUrl : notCheckUrlList) {
            if (reqUrl.startsWith(notCheckUrl)) {
                LOGGER.info("请求没有进行token校验，直接传达给业务下游");
                //直接将请求转给下游
                return chain.filter(exchange);
            }
        }
        //如果不存在url白名单，那么需要先尝试从请求头提取token（web前端），再回退到cookie校验
        List<HttpCookie> httpCookieList = request.getCookies().get("qytk");
        String qiyuTokenValue = null;
        if (!CollectionUtils.isEmpty(httpCookieList)) {
            qiyuTokenValue = httpCookieList.get(0).getValue();
        }
        if (StringUtils.isEmpty(qiyuTokenValue) || StringUtils.isEmpty(qiyuTokenValue.trim())) {
            //cookie中没有token时，回退读取请求头中的token
            qiyuTokenValue = request.getHeaders().getFirst("token");
        }
        if (StringUtils.isEmpty(qiyuTokenValue) || StringUtils.isEmpty(qiyuTokenValue.trim())) {
            LOGGER.error("请求没有检索到qytk的cookie或token请求头，被拦截");
            return writeUnauthorized(response);
        }
        //token获取到之后，调用rpc判断token是否合法，如果合法则吧token换取到的userId传递给到下游
        Long userId = accountTokenRPC.getUserIdByToken(qiyuTokenValue);
        //如果token不合法，则拦截请求，日志记录token失效
        if (userId == null) {
            LOGGER.error("请求的token失效了，被拦截");
            return writeUnauthorized(response);
        }
        //账号封禁校验：命中 ban:account:{userId} 直接 403（key 由 user-provider banUser 写入）
        if (isAccountBanned(userId)) {
            LOGGER.warn("请求的账号已被封禁, userId={}, url={}", userId, reqUrl);
            return writeForbidden(response);
        }
        // gateway --(header)--> springboot-web(interceptor-->get header)
        ServerHttpRequest.Builder builder = request.mutate();
        builder.header(GatewayHeaderEnum.USER_LOGIN_ID.getName(), String.valueOf(userId));
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private boolean isAccountBanned(Long userId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                    RiskConstants.BAN_ACCOUNT_KEY_PREFIX + userId));
        } catch (Exception e) {
            LOGGER.error("ban check redis error, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 封禁账号返回403和JSON报文
     */
    private Mono<Void> writeForbidden(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        byte[] body = "{\"code\":403,\"msg\":\"账号已被封禁，如有疑问请联系客服\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /**
     * 未登录/ token失效时返回401和JSON报文，便于前端统一处理
     */
    private Mono<Void> writeUnauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        byte[] body = "{\"code\":401,\"msg\":\"token invalid\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
