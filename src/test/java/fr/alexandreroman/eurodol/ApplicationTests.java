/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.eurodol;

import org.assertj.core.data.Percentage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 9876)
public class ApplicationTests {
    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;

    private UriComponentsBuilder url() {
        return UriComponentsBuilder.newInstance().scheme("http").host("localhost").port(port);
    }

    @Test
    public void contextLoads() {
    }

    @Test
    public void testCurrencyConverterEndpoint() {
        stubFor(get(urlEqualTo("/latest?base=USD&symbols=EUR"))
                .willReturn(okJson("{\"base\":\"USD\",\"rates\":{\"EUR\":0.8950948801},\"date\":\"2019-05-17\"}")
                        .withHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=14400")
                        .withHeader(HttpHeaders.EXPIRES,
                                Instant.now().atOffset(ZoneOffset.UTC).plusSeconds(14400).format(DateTimeFormatter.RFC_1123_DATE_TIME))));

        final String url = url().path("/api/v1/convert").queryParam("amount", 1).queryParam("symbol", "USD").toUriString();
        final ResponseEntity<CurrencyConversion> resp = restTemplate.getForEntity(url, CurrencyConversion.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        final CurrencyConversion conv = resp.getBody();
        assertThat(conv.getExchangeRate()).isCloseTo(BigDecimal.valueOf(0.8950948801), Percentage.withPercentage(5));
        assertThat(conv.getInput()).isEqualTo(new CurrencyAmount(BigDecimal.ONE, Currency.getInstance("USD")));
        assertThat(conv.getOutput()).isEqualTo(new CurrencyAmount(BigDecimal.valueOf(0.8950948801), Currency.getInstance("EUR")));

        verify(1, getRequestedFor(urlEqualTo("/latest?base=USD&symbols=EUR")));
    }

    @Test
    public void testCache() {
        stubFor(get(urlEqualTo("/latest?base=USD&symbols=EUR"))
                .willReturn(okJson("{\"base\":\"USD\",\"rates\":{\"EUR\":0.8950948801},\"date\":\"2019-05-17\"}")
                        .withHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=14400")
                        .withHeader(HttpHeaders.EXPIRES,
                                Instant.now().atOffset(ZoneOffset.UTC).plusSeconds(14400).format(DateTimeFormatter.RFC_1123_DATE_TIME))));

        final String url = url().path("/api/v1/convert").queryParam("amount", 1).queryParam("symbol", "USD").toUriString();
        assertThat(restTemplate.getForObject(url, CurrencyConversion.class)).isNotNull();
        assertThat(restTemplate.getForObject(url, CurrencyConversion.class)).isNotNull();
        assertThat(restTemplate.getForObject(url, CurrencyConversion.class)).isNotNull();

        verify(1, getRequestedFor(urlEqualTo("/latest?base=USD&symbols=EUR")));
    }

    @Test
    public void testInvalidSymbol() {
        final String url = url().path("/api/v1/convert").queryParam("amount", 1).queryParam("symbol", "FOO").toUriString();
        final ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void testUnsupportedSymbol() {
        final String url = url().path("/api/v1/convert").queryParam("amount", 1).queryParam("symbol", "AUD").toUriString();
        final ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void testInvalidAmount() {
        final String url = url().path("/api/v1/convert").queryParam("amount", "A").queryParam("symbol", "EUR").toUriString();
        final ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void testDefaultInputValues() {
        stubFor(get(urlEqualTo("/latest?base=EUR&symbols=USD"))
                .willReturn(okJson("{\"base\":\"EUR\",\"rates\":{\"USD\":1.1172},\"date\":\"2019-05-17\"}")
                        .withHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=14400")
                        .withHeader(HttpHeaders.EXPIRES,
                                Instant.now().atOffset(ZoneOffset.UTC).plusSeconds(14400).format(DateTimeFormatter.RFC_1123_DATE_TIME))));

        final String url = url().path("/api/v1/convert").toUriString();
        final ResponseEntity<CurrencyConversion> resp = restTemplate.getForEntity(url, CurrencyConversion.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        final CurrencyConversion conv = resp.getBody();
        assertThat(conv.getExchangeRate()).isCloseTo(BigDecimal.valueOf(1.1172), Percentage.withPercentage(5));
        assertThat(conv.getInput()).isEqualTo(new CurrencyAmount(BigDecimal.ONE, Currency.getInstance("EUR")));
        assertThat(conv.getOutput()).isEqualTo(new CurrencyAmount(BigDecimal.valueOf(1.1172), Currency.getInstance("USD")));

        verify(1, getRequestedFor(urlEqualTo("/latest?base=EUR&symbols=USD")));
    }

    @Test
    public void testHealthEndpoint() {
        final ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
