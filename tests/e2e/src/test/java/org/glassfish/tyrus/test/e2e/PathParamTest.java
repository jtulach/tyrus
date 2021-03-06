/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.EndpointConfig;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class PathParamTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "Hello World";

    @ServerEndpoint(value = "/pathparam/{first}/{second}/{third}")
    public static class PathParamTestEndpoint {

        @OnMessage
        public String doThat(@PathParam("first") String first,
                             @PathParam("second") String second,
                             @PathParam("third") String third,
                             @PathParam("fourth") String fourth,
                             String message, Session peer) {

            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertNull(fourth);
            assertNotNull(message);
            assertNotNull(peer);

            return message + first + second + third;
        }
    }

    @Test
    public void testPathParam() {
        Server server = new Server(PathParamTestEndpoint.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/pathparam/first/second/third"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(SENT_MESSAGE + "first" + "second" + "third", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/pathparam/{first}/{second}/")
    public static class PathParamTestBeanError {

        public static boolean onErrorCalled = false;
        public static Throwable onErrorThrowable = null;

        @OnMessage
        public String doThat(@PathParam("first") String first,
                             @PathParam("second") Integer second,
                             String message, Session peer) {

            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(message);
            assertNotNull(peer);

            return message + first + second;
        }

        @OnError
        public void onError(Throwable t) {
            onErrorCalled = true;
            onErrorThrowable = t;
        }
    }

    @Test
    public void testPathParamError() {
        Server server = new Server(PathParamTestBeanError.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/pathparam/first/second/"));
            messageLatch.await(1, TimeUnit.SECONDS);
            assertTrue(PathParamTestBeanError.onErrorCalled);
            assertNotNull(PathParamTestBeanError.onErrorThrowable);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/pathparam/{first}/{second}/")
    public static class PathParamTestBeanErrorNotPrimitive {

        @OnMessage
        public String doThat(@PathParam("first") String first,
                             @PathParam("second") PathParamTest second,
                             String message, Session peer) {

            return message + first + second;
        }
    }

    @Test
    public void testPathParamErrorNotPrimitive() {
        Server server = new Server(PathParamTestBeanErrorNotPrimitive.class);
        boolean exceptionThrown = false;

        try {
            server.start();
        } catch (Exception e) {
            exceptionThrown = true;
        } finally {
            server.stop();
            assertEquals(true, exceptionThrown);
        }
    }


    @ServerEndpoint(value = "/pathparam/{first}/{second}/{third}/{fourth}/{fifth}/{sixth}/{seventh}/{eighth}")
    public static class PathParamTestEndpointPrimitiveBoxing {

        @OnMessage
        public String doThat(@PathParam("first") String first,
                             @PathParam("second") Integer second,
                             @PathParam("third") Boolean third,
                             @PathParam("fourth") Long fourth,
                             @PathParam("fifth") Float fifth,
                             @PathParam("sixth") Double sixth,
                             @PathParam("seventh") Character seventh,
                             @PathParam("eighth") Byte eighth,
                             String message, Session peer) {
            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertNotNull(fourth);
            assertNotNull(fifth);
            assertNotNull(sixth);
            assertNotNull(seventh);
            assertNotNull(eighth);
            assertNotNull(message);
            assertNotNull(peer);

            return message + first + second + third + fourth + fifth + sixth + seventh + eighth;
        }
    }

    @ServerEndpoint(value = "/pathparam/{first}/{second}/{third}/{fourth}/{fifth}/{sixth}/{seventh}/{eighth}")
    public static class PathParamTestEndpointPrimitives {

        @OnMessage
        public String doThat(@PathParam("first") String first,
                             @PathParam("second") int second,
                             @PathParam("third") boolean third,
                             @PathParam("fourth") long fourth,
                             @PathParam("fifth") float fifth,
                             @PathParam("sixth") double sixth,
                             @PathParam("seventh") char seventh,
                             @PathParam("eighth") byte eighth,
                             String message, Session peer) {
            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertNotNull(fourth);
            assertNotNull(fifth);
            assertNotNull(sixth);
            assertNotNull(seventh);
            assertNotNull(eighth);
            assertNotNull(message);
            assertNotNull(peer);

            return message + first + second + third + fourth + fifth + sixth + seventh + eighth;
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testPathParamPrimitives() {
        testPathParamPrimitive(PathParamTestEndpointPrimitives.class);
    }

    @Test
    public void testPathParamPrimitivesBoxing() {
        testPathParamPrimitive(PathParamTestEndpointPrimitiveBoxing.class);
    }

    public void testPathParamPrimitive(Class<?> testedClass) {
        Server server = new Server(testedClass);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message: " + message);
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/pathparam/first/2/true/4/5/6/c/0"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(SENT_MESSAGE + "first" + "2" + "true" + "4" + "5.0" + "6.0" + "c" + "0", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }


}
