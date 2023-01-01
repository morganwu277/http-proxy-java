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

import ai.safekids.httpproxy.exception.NitmProxyException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static ai.safekids.httpproxy.listener.NitmProxyListenerProvider.*;

/**
 * This class provides more user-friendly api to configure the listeners.
 */
public class NitmProxyListenerStore {

    private List<NitmProxyListenerProvider> listeners;

    public NitmProxyListenerStore() {
        this(new ArrayList<>());
    }

    public NitmProxyListenerStore(
            List<NitmProxyListenerProvider> listeners) {
        this.listeners = listeners;
    }

    /**
     * Add the listener to the first place of the store.
     *
     * @param listener the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addFirst(NitmProxyListener listener) {
        return addFirst(singleton(listener));
    }

    /**
     * Add the provider to the first place of the store.
     *
     * @param provider the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addFirst(NitmProxyListenerProvider provider) {
        listeners.add(0, provider);
        return this;
    }

    /**
     * Add the listener to the place after the {@code target}.
     *
     * @param target the target class, can be a subclass of {@link NitmProxyListener}
     *               or {@link NitmProxyListenerProvider}
     * @param listener the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addAfter(Class<?> target, NitmProxyListener listener) {
        return addAfter(target, singleton(listener));
    }

    /**
     * Add the provider to the place after the {@code target}.
     *
     * @param target the target class, can be a subclass of {@link NitmProxyListener}
     *               or {@link NitmProxyListenerProvider}
     * @param provider the listener provider
     * @return the store itself
     */
    public NitmProxyListenerStore addAfter(Class<?> target, NitmProxyListenerProvider provider) {
        return addAfter(NitmProxyListenerProvider.match(target), provider);
    }

    /**
     * Add the listener to the place after the place that {@code predicate} was matched.
     *
     * @param predicate the predicate to determine the place for the listener to be inserted
     * @param listener the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addAfter(Predicate<NitmProxyListenerProvider> predicate, NitmProxyListener listener) {
        return addAfter(predicate, singleton(listener));
    }

    /**
     * Add the provider to the place after the place that {@code predicate} was matched.
     *
     * @param predicate the predicate to determine the place for the listener to be inserted
     * @param provider the provider
     * @return the store itself
     */
    public NitmProxyListenerStore addAfter(Predicate<NitmProxyListenerProvider> predicate,
            NitmProxyListenerProvider provider) {
        int matched = IntStream.range(0, listeners.size())
                               .filter(index -> predicate.test(listeners.get(index)))
                               .findFirst()
                               .orElseThrow(NitmProxyException.toThrow("Listener not exist in store: %s", predicate));
        listeners.add(matched + 1, provider);
        return this;
    }

    /**
     * Add the listener to the place before the {@code target}.
     *
     * @param target the target class, can be a subclass of {@link NitmProxyListener}
     *               or {@link NitmProxyListenerProvider}
     * @param listener the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addBefore(Class<?> target, NitmProxyListener listener) {
        return addBefore(target, singleton(listener));
    }

    /**
     * Add the provider to the place before the {@code target}.
     *
     * @param target the target class, can be a subclass of {@link NitmProxyListener}
     *               or {@link NitmProxyListenerProvider}
     * @param provider the provider
     * @return the store itself
     */
    public NitmProxyListenerStore addBefore(Class<?> target, NitmProxyListenerProvider provider) {
        return addBefore(NitmProxyListenerProvider.match(target), provider);
    }

    /**
     * Add the listener to the place before the place that {@code predicate} was matched.
     *
     * @param predicate the predicate to determine the place for the listener to be inserted
     * @param listener the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addBefore(Predicate<NitmProxyListenerProvider> predicate,
            NitmProxyListener listener) {
        return addBefore(predicate, singleton(listener));
    }

    /**
     * Add the provider to the place after the place that {@code predicate} was matched.
     *
     * @param predicate the predicate to determine the place for the listener to be inserted
     * @param provider the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addBefore(Predicate<NitmProxyListenerProvider> predicate,
            NitmProxyListenerProvider provider) {
        int matched = IntStream.range(0, listeners.size())
                               .filter(index -> predicate.test(listeners.get(index)))
                               .findFirst()
                               .orElseThrow(NitmProxyException.toThrow("Listener not exist in store: %s", predicate));
        listeners.add(matched, provider);
        return this;
    }

    /**
     * Add the listener to the last place of the store.
     *
     * @param listener the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addLast(NitmProxyListener listener) {
        return addLast(singleton(listener));
    }

    /**
     * Add the provider to the last place of the store.
     *
     * @param provider the listener
     * @return the store itself
     */
    public NitmProxyListenerStore addLast(NitmProxyListenerProvider provider) {
        listeners.add(provider);
        return this;
    }

    public List<NitmProxyListenerProvider> getListeners() {
        return listeners;
    }
}
