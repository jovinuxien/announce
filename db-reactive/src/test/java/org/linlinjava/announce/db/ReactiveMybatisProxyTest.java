package org.linlinjava.announce.db;

import org.junit.jupiter.api.Test;
import pro.chenggang.project.reactive.mybatis.support.r2dbc.support.ProxyInstanceFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * reactive-mybatis-support wraps the statement, the result handler and the connection of every mapper call in a
 * proxy from {@link ProxyInstanceFactory}. Up to 3.0.6 that factory generated and loaded a new ByteBuddy class on
 * every call (about 15 ms of CPU per statement and a class left behind each time); since 3.1.2 it uses a JDK proxy,
 * whose class the JDK caches per interface set. This test pins that behaviour so a downgrade or a regression of the
 * library is noticed here and not in a latency measurement.
 */
class ReactiveMybatisProxyTest {

    interface Port {
        String call();
    }

    @Test
    void theLibraryProxiesAreJdkProxiesWithOneClassPerInterfaceSet() {
        Supplier<InvocationHandler> handler = () -> (proxy, method, args) -> method.getName().equals("call") ? "ok" : null;
        Port first = ProxyInstanceFactory.newInstanceOfInterfaces(Port.class, handler, AutoCloseable.class);
        Port second = ProxyInstanceFactory.newInstanceOfInterfaces(Port.class, handler, AutoCloseable.class);

        assertThat(first.call()).isEqualTo("ok");
        assertThat(first).isInstanceOf(AutoCloseable.class);
        assertThat(Proxy.isProxyClass(first.getClass())).as("a JDK proxy, not a generated subclass").isTrue();
        assertThat(second.getClass()).as("the same class for the same interfaces").isSameAs(first.getClass());
    }
}
