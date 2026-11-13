package com.foundation.starter.client.appgrant;

/**
 * 应用间调用令牌（F-07 §9，**接口占位**）。
 *
 * <p>M2M：OAuth 2.0 {@code client_credentials}，{@code sub} = 服务账号、{@code aud} = 被调应用、
 * {@code act} = 调用方应用；OBO：OAuth 2.0 Token Exchange（RFC 8693），{@code sub} = 用户、
 * {@code act} = 应用 A。调用时透传 {@code request_id} 与 {@code traceparent}（ADR-073），
 * 并按 ADR-074 写 {@code ext_interaction} 台账。
 *
 * <p><b>实现归 BAS-30 / F-12</b>（ADR-076、S15 §22）；本包只登记接口形态。
 *
 * <p>本 starter 只依赖 {@code foundation-starter-core}（S15 §5.2），故 OBO 以 RFC 8693 的
 * {@code subject_token}（用户访问令牌原文）表达用户身份，不引用 security 的 {@code Principal}。
 */
public interface AppTokenClient {

    /** M2M：取面向被调应用的令牌。 */
    String clientCredentials(String targetAppCode);

    /** OBO（RFC 8693）：以用户访问令牌换取面向被调应用的令牌。 */
    String onBehalfOf(String targetAppCode, String subjectToken);
}
