package com.campusconnect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real web server and opens the main pages, like a browser would.
 * Template mistakes (for example a missing value) only show up when a page is drawn,
 * so this catches errors that the service tests cannot see.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:pagetest")
class PageRenderTests {

    @Autowired
    private Environment environment;

    private HttpClient browser;
    private String base;

    @BeforeEach
    void openBrowser() throws Exception {
        // The cookie manager keeps the session cookie, so "unlocked" is remembered between requests
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
        post("/g/GRIT/unlock", "passphrase=dev-grit");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String form) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void groupPagesOpen() throws Exception {
        String[] pages = {"/", "/groups/new", "/admin", "/g/GRIT", "/g/GRIT/people", "/g/GRIT/attendance-rate",
                "/g/GRIT/events/new", "/g/GRIT/events/EVT001", "/g/GRIT/events/EVT001/live",
                "/g/GRIT/events/EVT001/checkout-all", "/g/GRIT/people/987654321/edit?from=live&eventId=EVT001",
                "/g/GRIT/events/EVT001/report.csv"};
        for (String page : pages) {
            assertEquals(200, get(page).statusCode(), page);
        }
        assertTrue(get("/g/GRIT/events/EVT001/live").body().contains("came in total"));
    }

    @Test
    void checkInAndCheckOutPagesOpen() throws Exception {
        assertEquals(200, get("/events/EVT003/checkin").statusCode());
        assertEquals(200, get("/events/EVT003/checkout").statusCode());

        // Known Student ID: check-in result with "Not you?"
        HttpResponse<String> in = post("/events/EVT003/checkin", "studentId=123456789");
        assertEquals(200, in.statusCode());
        assertTrue(in.body().contains("Not you?"));

        // Check-out result page (this page crashed once because "knownId" was missing)
        HttpResponse<String> out = post("/events/EVT003/checkout", "studentId=123456789");
        assertEquals(200, out.statusCode());
        assertTrue(out.body().contains("Checked out"));

        // Already checked out
        assertEquals(200, post("/events/EVT003/checkout", "studentId=123456789").statusCode());
        // New Student ID: guest form, then the result page
        assertEquals(200, post("/events/EVT003/checkin", "studentId=777000111").statusCode());
        assertEquals(200, post("/events/EVT003/checkin/guest",
                "studentId=777000111&name=Page+Test&email=p%40example.com").statusCode());
    }
}
