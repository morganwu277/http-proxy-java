/*
 * Copyright 2021 - Safe Kids LLC
 *
 * The complete license agreement is available at https://safekids.ai/eula
 *
 *
 * LICENSE GRANT
 * ==============================================
 * Licensor hereby grants to you a non-exclusive and non-transferable license to use the Software and
 * related documentation (the "Documentation") solely for the intended purposes of the Software as set forth in the
 * Documentation, according to the provisions contained herein and subject to payment of applicable license fees.
 * You are not permitted to lease, rent, distribute or sublicense the Software or any rights therein.
 * You also may not install the Software on a network server, use the Software in a time-sharing arrangement or
 * in any other unauthorized manner. Further, no license is granted to you in the human readable code of the Software
 *  (source code). Except as provided below, this Agreement does not grant you any rights to patents, copyrights,
 *  trade secrets, trademarks, or any other rights in the Software and Documentation.
 *
 * NO MODIFICATION, NO REVERSE ENGINEERING
 * ===============================================
 * You agree not to, without the prior written permission of Licensor: (i); disassemble, decompile or "unlock",
 * decode or otherwise reverse translate or engineer, or attempt in any manner to reconstruct or discover any source
 * code or underlying algorithms of the Software, if provided in object code form only; (ii) use, copy,
 * modify, translate,reverse engineer, decompile, disassemble, or create derivative works of the Software and
 * any accompanying documents, or assist someone in performing such prohibited acts; or (iii) transfer, rent,
 * lease, or sub license the Software.
 *
 * NO WARRANTIES.
 * ===============================================
 * LICENSOR MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT OF THIRD PARTIES' RIGHTS.
 * THE SOFTWARE (INCLUDING SOURCE CODE) IS PROVIDED TO YOU ON AN "AS IS" BASIS. TO THE FULL EXTENT PERMITTED BY LAW,
 * THE DURATION OF STATUTORILY REQUIRED WARRANTIES, IF ANY, SHALL BE LIMITED TO THE ABOVE LIMITED WARRANTY PERIOD.
 * MOREOVER, IN NO EVENT WILL WARRANTIES PROVIDED BY LAW, IF ANY, APPLY UNLESS THEY ARE REQUIRED TO APPLY BY
 * STATUTE NOTWITHSTANDING THEIR EXCLUSION BY CONTRACT. NO DEALER, AGENT, OR EMPLOYEE OF LICENSOR IS AUTHORIZED TO
 * MAKE ANY MODIFICATIONS, EXTENSIONS, OR ADDITIONS TO THIS LIMITED WARRANTY. THE ENTIRE RISK ARISING OUT OF USE OR
 * PERFORMANCE OF THE SOFTWARE REMAINS WITH YOU.
 *
 */

package ai.safekids.httpproxy.listener;

import ai.safekids.httpproxy.testing.NitmProxyListenerStoreAssert;
import org.assertj.core.api.AssertProvider;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.util.function.Consumer;

import static com.google.common.base.Predicates.*;
import static org.assertj.core.api.Assertions.*;


public class NitmProxyListenerStoreTest {

    @Test
    public void shouldAddBefore() {
        assertThat(configure(store -> store.addBefore(Pivot.class, new ListenerA())))
                .hasListeners(listener(ListenerA.class), listener(Pivot.class));
        assertThat(configure(store -> store.addBefore(PivotProvider.class, new ProviderA())))
                .hasListeners(listener(ProviderA.class), listener(Pivot.class));
        assertThat(configure(store -> store.addBefore(alwaysTrue(), new ListenerA())))
                .hasListeners(listener(ListenerA.class), listener(Pivot.class));
    }

    @Test
    public void shouldAddAfter() {
        assertThat(configure(store -> store.addAfter(Pivot.class, new ListenerA())))
                .hasListeners(listener(Pivot.class), listener(ListenerA.class));
        assertThat(configure(store -> store.addAfter(PivotProvider.class, new ProviderA())))
                .hasListeners(listener(Pivot.class), listener(ProviderA.class));
        assertThat(configure(store -> store.addAfter(alwaysTrue(), new ListenerA())))
                .hasListeners(listener(Pivot.class), listener(ListenerA.class));
    }

    @Test
    public void shouldAddFirst() {
        assertThat(configure(store -> store.addFirst(new ListenerA())))
                .hasListeners(listener(ListenerA.class), listener(Pivot.class));
        assertThat(configure(store -> store.addFirst(new ProviderA())))
                .hasListeners(listener(ProviderA.class), listener(Pivot.class));
    }

    @Test
    public void shouldAddLast() {
        assertThat(configure(store -> store.addLast(new ListenerA())))
                .hasListeners(listener(Pivot.class), listener(ListenerA.class));
        assertThat(configure(store -> store.addLast(new ProviderA())))
                .hasListeners(listener(Pivot.class), listener(ProviderA.class));
    }

    private static AssertProvider<NitmProxyListenerStoreAssert> configure(Consumer<NitmProxyListenerStore> consumer) {
        NitmProxyListenerStore listenerStore = new NitmProxyListenerStore();
        listenerStore.addLast(new PivotProvider());
        consumer.accept(listenerStore);
        return () -> new NitmProxyListenerStoreAssert(listenerStore);
    }

    private static Condition<NitmProxyListenerProvider> listener(Class<?> type) {
        return new Condition<>(NitmProxyListenerProvider.match(type), type.getTypeName());
    }

    private static class Pivot implements NitmProxyListener {}

    private static class PivotProvider implements NitmProxyListenerProvider {
        private static final Pivot INSTANCE = new Pivot();

        @Override
        public NitmProxyListener create() {
            return INSTANCE;
        }

        @Override
        public Class<? extends NitmProxyListener> listenerClass() {
            return Pivot.class;
        }
    }

    private static class ListenerA implements NitmProxyListener {}

    private static class ProviderA implements NitmProxyListenerProvider {
        @Override
        public NitmProxyListener create() {
            return new ListenerA();
        }

        @Override
        public Class<? extends NitmProxyListener> listenerClass() {
            return ListenerA.class;
        }
    }
}
