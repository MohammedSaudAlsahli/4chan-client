package app.boardwalk.data

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class NetworkTest {
    @Test fun proxyValidationRejectsMalformedInputs() {
        assertNotNull(ProxySettings(true, "https://proxy.example", "8080").validationError())
        assertNotNull(ProxySettings(true, "proxy.example", "65536").validationError())
        assertNotNull(ProxySettings(true, "proxy.example", "0").validationError())
        assertNotNull(ProxySettings(true, "proxy.example", "abc").validationError())
        assertNull(ProxySettings(true, "proxy.example", "8080").validationError())
    }

    @Test fun enabledProxyIsExplicitAndDirectModeIsExplicit() {
        val proxy = NetworkFactory.create(ProxySettings(true, "proxy.example", "8080")).proxy!!
        assertEquals(Proxy.Type.HTTP, proxy.type())
        assertEquals("proxy.example", (proxy.address() as InetSocketAddress).hostString)
        assertEquals(Proxy.NO_PROXY, NetworkFactory.create(ProxySettings()).proxy)
    }

    @Test fun requestUsesProxyWithoutResolvingOriginLocally() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setBody("through proxy"))
            proxy.start()
            val client = NetworkFactory.create(ProxySettings(true, proxy.hostName, proxy.port.toString()))
            client.newCall(Request.Builder().url("http://unresolvable-boardwalk.invalid/boards.json").build()).execute().use {
                assertEquals("through proxy", it.body!!.string())
            }
            assertTrue(proxy.takeRequest().requestLine.contains("http://unresolvable-boardwalk.invalid/boards.json"))
        }
    }

    @Test fun proxyAuthenticationDoesNotUseOriginAuthorizationHeader() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setResponseCode(407).setHeader("Proxy-Authenticate", "Basic realm=proxy"))
            proxy.enqueue(MockResponse().setBody("ok"))
            proxy.start()
            val client = NetworkFactory.create(ProxySettings(true, proxy.hostName, proxy.port.toString(), "reader", "secret"))
            client.newCall(Request.Builder().url("http://origin.invalid/").build()).execute().close()
            proxy.takeRequest()
            val authenticated = proxy.takeRequest()
            assertNotNull(authenticated.getHeader("Proxy-Authorization"))
            assertNull(authenticated.getHeader("Authorization"))
        }
    }

    @Test fun failedProxyNeverFallsBackToDirectConnection() {
        MockWebServer().use { origin ->
            origin.start()
            val closedProxy = MockWebServer().apply { start() }
            val port = closedProxy.port
            closedProxy.shutdown()
            val client = NetworkFactory.create(ProxySettings(true, "127.0.0.1", port.toString()))
                .newBuilder().callTimeout(2, TimeUnit.SECONDS).build()
            assertTrue(runCatching { client.newCall(Request.Builder().url(origin.url("/")).build()).execute().close() }.isFailure)
            assertEquals(0, origin.requestCount)
        }
    }

    @Test fun httpsUsesConnectTunnelAndDoesNotResolveTheOriginLocally() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setResponseCode(407).setHeader("Proxy-Authenticate", "Basic realm=proxy"))
            proxy.start()
            val client = NetworkFactory.create(ProxySettings(true, proxy.hostName, proxy.port.toString(), "reader", "secret"))
            assertTrue(runCatching {
                client.newCall(Request.Builder().url("https://unresolvable-boardwalk.invalid/boards.json").build()).execute().close()
            }.isFailure)
            val tunnel = proxy.takeRequest()
            assertEquals("CONNECT", tunnel.method)
            assertTrue(tunnel.requestLine.contains("unresolvable-boardwalk.invalid:443"))
            assertNotNull(tunnel.getHeader("Proxy-Authorization"))
            assertNull(tunnel.getHeader("Authorization"))
        }
    }

    @Test fun rapidThreadRefreshUsesCachedBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"posts\":[{\"no\":123,\"com\":\"hello\"}]}"))
            val api = BoardApi(NetworkFactory.create(ProxySettings()), ApiGate(), server.url("/").toString())
            assertEquals(123L, api.thread("g", 123).first().id)
            assertEquals("hello", api.thread("g", 123).first().comment)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun conditionalRefreshKeepsCachedBodyAfter304() = runBlocking {
        MockWebServer().use { server ->
            var time = 0L
            server.enqueue(MockResponse().setHeader("Last-Modified", "Mon, 05 Oct 2026 10:00:00 GMT")
                .setBody("{\"posts\":[{\"no\":123}]}"))
            server.enqueue(MockResponse().setResponseCode(304))
            val api = BoardApi(NetworkFactory.create(ProxySettings()), ApiGate(), server.url("/").toString(), { time })
            api.thread("g", 123)
            time = 16_000
            assertEquals(123L, api.thread("g", 123).first().id)
            server.takeRequest()
            assertEquals("Mon, 05 Oct 2026 10:00:00 GMT", server.takeRequest().getHeader("If-Modified-Since"))
        }
    }
}
