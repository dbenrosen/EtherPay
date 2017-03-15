package org.ethereum.net.eth.handler;

import org.ethereum.net.eth.EthVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Default factory implementation
 *
 * @author Mikhail Kalinin
 * @since 20.08.2015
 */
@Component
public class EthHandlerFactoryImpl implements EthHandlerFactory {

    @Autowired
    private ApplicationContext ctx;

    @Override
    public EthHandler create(EthVersion version) {
        switch (version) {
            case V62:   return (EthHandler) ctx.getBean("Eth62");
            case V63:   return (EthHandler) ctx.getBean("Eth63");
            default:    throw new IllegalArgumentException("Eth " + version + " is not supported");
        }
    }
}
