package org.linlinjava.announce.cqrs.readside;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * Registers one OpenCQRS {@code EventHandlerDefinition} bean per {@link ReactiveProjection} bean, the way
 * OpenCQRS itself registers {@code @EventHandling} methods, so the processors are created for the projection's
 * group without the projection knowing about OpenCQRS.
 */
public class ReactiveProjectionRegistrar implements BeanDefinitionRegistryPostProcessor {

    static final String SUFFIX = "EventHandlerDefinition";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!(registry instanceof ConfigurableListableBeanFactory beanFactory)) {
            return;
        }
        for (String projectionBean : beanFactory.getBeanNamesForType(ReactiveProjection.class, true, false)) {
            String definitionBean = projectionBean + SUFFIX;
            if (registry.containsBeanDefinition(definitionBean)) {
                continue;
            }
            registry.registerBeanDefinition(definitionBean, BeanDefinitionBuilder
                    .genericBeanDefinition(ReactiveProjectionAdapter.class)
                    .setFactoryMethod("definition")
                    .addConstructorArgReference(projectionBean)
                    .addConstructorArgReference(ReactiveProjectionSupport.BEAN_NAME)
                    .getBeanDefinition());
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nothing to do
    }
}
