# JWT 认证:原理 + Spring Security 集成

> 适用场景:前后端分离、微服务的登录认证。面试必问,项目必用。本文讲清楚 JWT 是什么、为什么用它、完整登录流程怎么走、有哪些坑。

## 1. 是什么:JWT 就是一张"自带签名的通行证"

JWT(JSON Web Token)是一串字符串,由三部分组成,用 `.` 分隔:

```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsImV4cCI6MTc4Nzc5NTIwMH0.xxxxxxxx(签名,示例省略)

> 示例:Header 为 `{"alg":"HS256","typ":"JWT"}`,Payload 为 `{"userId":1,"exp":...}`。
```

三段分别是:

| 段 | 内容 | 说明 |
|----|------|------|
| Header(头部) | `{"alg":"HS256","typ":"JWT"}` | 签名算法,Base64URL 编码 |
| Payload(载荷) | `{"userId":1,"exp":1787795200}` | 业务数据(别放密码!),Base64URL 编码 |
| Signature(签名) | `HMACSHA256(header.payload, 密钥)` | 用密钥对前两段算签名,防篡改 |

**关键点:前两段只是 Base64 编码,不是加密,谁都能解开看;签名才是防篡改的关键。** 密钥只有服务器知道,谁改了一个字节,签名就对不上。

## 2. 为什么:对比传统 Session

**传统 Session 登录:**
1. 登录成功 → 服务器生成 sessionId,存一份在服务器内存/Redis;
2. 返回给浏览器存 Cookie;
3. 下次请求带 Cookie → 服务器查 session。

问题:
- 服务器要**存**状态,多台机器部署时 session 不共享(得做 session 粘滞或统一 Redis);
- 前后端分离时,Cookie 跨域麻烦。

**JWT 登录:**
1. 登录成功 → 服务器签发一个 token(不存任何东西)返回;
2. 前端存 localStorage,请求头带 `Authorization: Bearer <token>`;
3. 服务器**只验签**,验签通过就信任里面的 userId,完全不查库。

优点:**无状态、天然分布式友好**——任何一台服务器只要知道密钥,都能验证 token 合法性。这就是微服务选 JWT 的根本原因。

## 3. 登录流程(完整链路)

```
客户端                         网关/认证服务                      业务服务
  │  1. POST /login {账号,密码}   │                              │
  │ ──────────────────────────>   │ 2. 查库校验密码(BCrypt)       │
  │                               │ 3. 生成 JWT(含userId+过期)     │
  │  4. 返回 { token }            │                              │
  │ <──────────────────────────   │                              │
  │  5. 后续请求带 Authorization  │                              │
  │ ────────────────────────────────────────────────────────>   │
  │                               │                              │ 6. 过滤器验签
  │                               │                              │ 7. 放行/返回用户信息
```

## 4. 代码实现(Spring Boot + Spring Security + jjwt)

### 4.1 依赖(pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- JWT 工具库 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

### 4.2 JWT 工具类(生成 + 解析)

```java
package com.example.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    /** 密钥:生产环境放配置中心/环境变量,严禁硬编码! */
    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "your-jwt-secret-key-please-change-it-32bytes-min".getBytes(StandardCharsets.UTF_8));

    /** token 有效期:2 小时 */
    private static final long EXPIRE = 2 * 60 * 60 * 1000L;

    /** 生成 token */
    public static String createToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))          // 主体:userId
                .claim("username", username)                  // 自定义字段
                .setIssuedAt(now)                             // 签发时间
                .setExpiration(new Date(now.getTime() + EXPIRE)) // 过期时间
                .signWith(KEY, SignatureAlgorithm.HS256)      // 签名
                .compact();
    }

    /** 解析 token,失败(过期/被篡改)抛异常 */
    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### 4.3 登录接口(校验密码 → 发 token)

```java
package com.example.auth.controller;

import com.example.auth.entity.LoginUser;
import com.example.auth.util.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LoginController {

    /** 密码加密器(存库的密码都是 BCrypt 密文,不存明文) */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginUser loginUser) {
        // 1. 模拟查库:真实项目从 MySQL/Redis 查用户
        //    String passwordInDb = userMapper.selectPasswordByUsername(loginUser.getUsername());
        String passwordInDb = "$2a$10$..."; // BCrypt 密文示例

        // 2. 校验密码(BCrypt 自带盐,不能用 equals 比较!)
        if (!encoder.matches(loginUser.getPassword(), passwordInDb)) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 3. 签发 token
        String token = JwtUtil.createToken(1L, loginUser.getUsername());
        return Map.of("token", token);
    }
}
```

### 4.4 认证过滤器(每个请求验签)

```java
package com.example.auth.filter;

import com.example.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 请求进来先过这个过滤器:
 * 1. 从 Authorization 头取出 token
 * 2. 验签 + 解析
 * 3. 把用户信息塞进 SecurityContext,后续接口就能拿到当前用户
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = JwtUtil.parseToken(token);   // 验签,失败抛异常
                Long userId = Long.valueOf(claims.getSubject());
                String username = (String) claims.get("username");

                // 放入 Spring Security 上下文(角色权限可在这里一起塞)
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userId, null, AuthorityUtils.NO_AUTHORITIES);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // token 无效/过期:不设置认证信息,后续会被安全配置拦下
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
```

### 4.5 安全配置(放行登录接口,其他都要认证)

```java
package com.example.auth.config;

import com.example.auth.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 无状态:不用 Session,每次请求都靠 token
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login").permitAll()   // 登录接口放行
                .requestMatchers("/notes/**").permitAll() // 示例:公开接口
                .anyRequest().authenticated()            // 其他都要登录
            )
            // 把 JWT 过滤器加在用户名密码过滤器之前
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 4.6 业务接口里拿当前用户

```java
@GetMapping("/me")
public Map<String, Object> me() {
    // 过滤器已经把 userId 塞进 SecurityContext 了
    Object userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return Map.of("userId", userId);
}
```

前端调用时统一带请求头:

```js
// axios 拦截器示例
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

## 5. 面试口径

**Q: JWT 和 Session 的区别?为什么微服务用 JWT?**
A: Session 需要服务器存状态,多实例部署要共享存储;JWT 无状态,服务器只验签不存东西,任何实例都能验证,天然适合微服务和前后端分离。缺点是无法主动注销、token 泄露难回收。

**Q: JWT 由哪几部分组成?**
A: Header(算法)+ Payload(业务数据)+ Signature(签名)。前两段 Base64URL 编码可被任何人解码,签名用密钥对前两段计算,防篡改。

**Q: 密码为什么用 BCrypt 不用 MD5?**
A: MD5 是定长摘要、无盐、可查彩虹表;BCrypt 内置随机盐且每次加密结果不同,慢哈希抗暴力破解,`matches()` 校验。

**Q: token 过期了怎么办?**
A: 前端收到 401 跳登录页重新登录;想体验好可以做"刷新 token"(refresh token 有效期长,用来换新 access token)。

**Q: JWT 能主动注销吗?**
A: 标准 JWT 不行,它无状态。方案:① 把 token 加入 Redis 黑名单,过期时间对齐;② token 有效期设短 + 刷新机制;③ 用户改密码后让 token 失效(Redis 存版本号)。

## 6. 踩坑提醒

1. **密钥必须保护**:硬编码在代码里=裸奔,要放环境变量/配置中心;泄露后任何人都能伪造 token。
2. **Payload 别放敏感信息**:密码、手机号别塞进去,Base64 谁都能解。
3. **有效期别太长**:2 小时左右合理,配合刷新 token。
4. **过滤器要放行登录和公开接口**,否则登录接口自己都被拦。
5. **BCrypt 校验用 `matches()`,不能 `equals()`**:因为每次加密带随机盐,密文不同。
6. **`jwt` 库版本坑**:jjwt 0.9.x 和 0.11.x API 完全不同,0.11 用 `parserBuilder()`,0.9 用 `parseClaimsJws()` 直接调,别混。
