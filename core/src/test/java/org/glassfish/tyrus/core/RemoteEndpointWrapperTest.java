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

package org.glassfish.tyrus.core;


import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.spi.RemoteEndpoint;
import org.glassfish.tyrus.websockets.DataFrame;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the RemoteEndpointWrapper.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class RemoteEndpointWrapperTest {

    private final byte[] sentBytes = {'a', 'b', 'c'};
    private final byte[] sentBytesComplete = {'a', 'b', 'c', 'a', 'b', 'c'};
    private final TyrusEndpointWrapper ew = new TyrusEndpointWrapper(EchoEndpoint.class, null, null, new TestContainer(), null, null, null);

    @Test
    public void testGetSendStream() throws IOException {

        TestRemoteEndpoint tre = new TestRemoteEndpoint();
        TyrusSession testSession = new TyrusSession(null, tre, ew, null, null, true, null, null, Collections.<String, String>emptyMap(), null, new HashMap<String, List<String>>());
        RemoteEndpointWrapper.Basic rew = new RemoteEndpointWrapper.Basic(testSession, tre, ew);
        OutputStream stream = rew.getSendStream();

        for (byte b : sentBytes) {
            stream.write(b);
        }

        stream.flush();

        // Assert.assertArrayEquals("Writing bytes one by one to stream and flushing.", sentBytes, tre.getBytesAndClearBuffer());

        stream.write(sentBytes);
        stream.close();

        Assert.assertArrayEquals("Writing byte[] to stream and flushing.", sentBytesComplete, tre.getBytesAndClearBuffer());
    }

    @Test
    public void testGetSendStreamWriteArrayWhole() throws IOException {

        TestRemoteEndpoint tre = new TestRemoteEndpoint();
        TyrusSession testSession = new TyrusSession(null, tre, ew, null, null, true, null, null, Collections.<String, String>emptyMap(), null, new HashMap<String, List<String>>());
        RemoteEndpointWrapper.Basic rew = new RemoteEndpointWrapper.Basic(testSession, tre, ew);
        OutputStream stream = rew.getSendStream();

        stream.write(sentBytesComplete);
        Assert.assertEquals(6, tre.getLastSentMessageSize());
        stream.close();
        Assert.assertEquals(0, tre.getLastSentMessageSize());

        Assert.assertArrayEquals("Writing byte[] to stream and flushing.", sentBytesComplete, tre.getBytesAndClearBuffer());
    }

    @Test
    public void testGetSendStreamWriteArrayPerPartes() throws IOException {

        TestRemoteEndpoint tre = new TestRemoteEndpoint();
        TyrusSession testSession = new TyrusSession(null, tre, ew, null, null, true, null, null, Collections.<String, String>emptyMap(), null, new HashMap<String, List<String>>());
        RemoteEndpointWrapper.Basic rew = new RemoteEndpointWrapper.Basic(testSession, tre, ew);
        OutputStream stream = rew.getSendStream();

        stream.write(sentBytes);
        Assert.assertEquals(3, tre.getLastSentMessageSize());
        stream.write(sentBytes);
        Assert.assertEquals(3, tre.getLastSentMessageSize());
        stream.close();
        Assert.assertEquals(0, tre.getLastSentMessageSize());

        Assert.assertArrayEquals("Writing byte[] to stream and flushing.", sentBytesComplete, tre.getBytesAndClearBuffer());
    }


    @Test
    public void testGetSendWriter() throws IOException {
        final String sentString = "abc";

        char[] toSend = sentString.toCharArray();
        TestRemoteEndpoint tre = new TestRemoteEndpoint();
        TyrusSession testSession = new TyrusSession(null, tre, ew, null, null, true, null, null, Collections.<String, String>emptyMap(), null, new HashMap<String, List<String>>());
        RemoteEndpointWrapper.Basic rew = new RemoteEndpointWrapper.Basic(testSession, tre, ew);
        Writer writer = rew.getSendWriter();

        writer.write(toSend, 0, 3);
        writer.flush();
        Assert.assertEquals("Writing the whole message.", sentString, tre.getStringAndCleanBuilder());

        writer.write(toSend, 0, 1);
        writer.flush();
        Assert.assertEquals("Writing first character.", String.valueOf(toSend[0]), tre.getStringAndCleanBuilder());

        writer.write(toSend, 2, 1);
        writer.flush();
        Assert.assertEquals("Writing first character.", String.valueOf(toSend[2]), tre.getStringAndCleanBuilder());
    }


    private class TestRemoteEndpoint extends RemoteEndpoint {

        private final ArrayList<Byte> bytesToSend = new ArrayList<Byte>();
        StringBuilder builder = new StringBuilder();
        private int lastSentMessageSize;

        @Override
        public Future<DataFrame> sendText(String text) throws IOException {
            return null;
        }

        @Override
        public Future<DataFrame> sendBinary(ByteBuffer data) throws IOException {
            return null;
        }

        @Override
        public Future<DataFrame> sendText(String fragment, boolean isLast) throws IOException {
            builder.append(fragment);
            return null;
        }

        @Override
        public Future<DataFrame> sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
            byte[] bytes = partialByte.array();
            lastSentMessageSize = bytes.length;
            for (byte b : bytes) {
                bytesToSend.add(b);
            }
            return null;
        }

        @Override
        public Future<DataFrame> sendPing(ByteBuffer applicationData) {
            return null;
        }

        @Override
        public Future<DataFrame> sendPong(ByteBuffer applicationData) {
            return null;
        }

        public byte[] getBytesAndClearBuffer() {
            byte[] result = new byte[bytesToSend.size()];

            for (int i = 0; i < bytesToSend.size(); i++) {
                result[i] = bytesToSend.get(i);
            }

            bytesToSend.clear();
            return result;
        }

        public String getStringAndCleanBuilder() {
            String result = builder.toString();
            builder = new StringBuilder();
            return result;
        }

        @Override
        public void close(CloseReason closeReason) {

        }

        @Override
        public void setWriteTimeout(long timeoutMs) {

        }

        private int getLastSentMessageSize() {
            return lastSentMessageSize;
        }
    }

    @ServerEndpoint(value = "/echo")
    private static class EchoEndpoint {

        @OnMessage
        public String doThat(String message, Session peer) {
            return message;
        }
    }

    private static class TestContainer extends BaseContainer {

        @Override
        public long getDefaultAsyncSendTimeout() {
            return 0;
        }

        @Override
        public void setAsyncSendTimeout(long l) {

        }

        @Override
        public Session connectToServer(Object o, URI uri) throws DeploymentException, IOException {
            return null;
        }

        @Override
        public Session connectToServer(Class<?> aClass, URI uri) throws DeploymentException, IOException {
            return null;
        }

        @Override
        public Session connectToServer(Endpoint endpoint, ClientEndpointConfig clientEndpointConfig, URI uri) throws DeploymentException, IOException {
            return null;
        }

        @Override
        public Session connectToServer(Class<? extends Endpoint> aClass, ClientEndpointConfig clientEndpointConfig, URI uri) throws DeploymentException, IOException {
            return null;
        }

        @Override
        public long getDefaultMaxSessionIdleTimeout() {
            return 0;
        }

        @Override
        public void setDefaultMaxSessionIdleTimeout(long l) {

        }

        @Override
        public int getDefaultMaxBinaryMessageBufferSize() {
            return 0;
        }

        @Override
        public void setDefaultMaxBinaryMessageBufferSize(int i) {

        }

        @Override
        public int getDefaultMaxTextMessageBufferSize() {
            return 0;
        }

        @Override
        public void setDefaultMaxTextMessageBufferSize(int i) {

        }

        @Override
        public Set<Extension> getInstalledExtensions() {
            return null;
        }

        @Override
        public ScheduledExecutorService getScheduledExecutorService() {
            return null;
        }
    }
}
