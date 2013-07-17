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

package org.glassfish.tyrus.sample.echo.https;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.fail;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class EchoTest {

    private final String CONTEXT_PATH = "/sample-echo-https";
    private final String DEFAULT_HOST = "localhost";
    private final int DEFAULT_PORT = 8181;

    /**
     * Start embedded server unless "tyrus.test.host" system property is specified.
     *
     * @return new {@link Server} instance or {@code null} if "tyrus.test.host" system property is set.
     */
    private Server startServer() throws DeploymentException {
        // glassfish only sample
        return null;
    }

    private String getHost() {
        final String host = System.getProperty("tyrus.test.host");
        if (host != null) {
            return host;
        }
        return DEFAULT_HOST;
    }

    private int getPort() {
        final String port = System.getProperty("tyrus.test.port");
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException nfe) {
                // do nothing
            }
        }
        return DEFAULT_PORT;
    }

    private URI getURI() {
        try {
            return new URI("wss", null, getHost(), getPort(), CONTEXT_PATH + "/echo", null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void stopServer(Server server) {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testEcho() throws DeploymentException, InterruptedException, IOException {
        if(System.getProperty("tyrus.test.host") == null) {
            return;
        }

        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(1);
        final CountDownLatch onOpenLatch = new CountDownLatch(1);

        final ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("### Received: " + message);

                            if(message.equals("Do or do not, there is no try. (from your server)")) {
                                messageLatch.countDown();
                            } else if (message.equals("onOpen")) {
                                onOpenLatch.countDown();
                            }
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    // do nothing
                }
            }
//        }, ClientEndpointConfig.Builder.create().build(), getURI());
        }, ClientEndpointConfig.Builder.create().build(), getURI());

        messageLatch.await(1, TimeUnit.SECONDS);
        if (messageLatch.getCount() != 0 || onOpenLatch.getCount() != 0) {
            fail();
        }

        stopServer(server);
    }
}
