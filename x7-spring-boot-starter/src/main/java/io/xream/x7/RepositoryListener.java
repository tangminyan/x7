/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7;

import io.xream.x7.cache.*;
import io.xream.x7.cache.customizer.L2CacheStoragePolicyCustomizer;
import io.xream.x7.cache.customizer.L3CacheArgsToStringCustomizer;
import io.xream.x7.cache.customizer.L3CacheStoragePolicyCustomizer;
import io.xream.x7.repository.id.IdGeneratorPolicy;
import io.xream.x7.repository.id.IdGeneratorService;
import io.xream.x7.repository.id.customizer.IdGeneratorPolicyCustomizer;
import io.xream.x7.repository.schema.SchemaConfig;
import io.xream.x7.repository.schema.SchemaTransformRepository;
import io.xream.x7.repository.schema.customizer.SchemaTransformCustomizer;
import io.xream.x7.repository.schema.customizer.SchemaTransformRepositoryBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import io.xream.x7.common.bean.BeanElement;
import io.xream.x7.common.bean.Parsed;
import io.xream.x7.common.bean.Parser;
import io.xream.x7.common.bean.TransformConfigurable;
import io.xream.x7.common.repository.CacheResolver;
import io.xream.x7.repository.BaseRepository;
import io.xream.x7.repository.CacheableRepository;
import io.xream.x7.repository.Repository;
import io.xream.x7.repository.RepositoryBootListener;
import io.xream.x7.repository.mapper.MapperFactory;
import io.xream.x7.repository.transform.DataTransform;
import io.xream.x7.repository.transform.customizer.DataTransformCustomizer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RepositoryListener implements
        ApplicationListener<ApplicationStartedEvent> {


    @Override
    public void onApplicationEvent(ApplicationStartedEvent applicationStartedEvent) {


        customizeL3CacheArgsToString(applicationStartedEvent);
        customizeL3CacheStoragePolicy(applicationStartedEvent);

        if (!X7Data.isEnabled)
            return;

        customizeCacheStoragePolicy(applicationStartedEvent);

        customizeIdGeneratorPolicy(applicationStartedEvent);

        customizeDataTransform(applicationStartedEvent);

        RepositoryBootListener.onStarted(applicationStartedEvent.getApplicationContext());

        transform(applicationStartedEvent);
    }

    private void customizeL3CacheStoragePolicy(ApplicationStartedEvent applicationStartedEvent) {

        L3CacheAspect bean = null;
        try {
            bean = applicationStartedEvent.getApplicationContext().getBean(L3CacheAspect.class);
        } catch (Exception e) {
        }
        if (bean == null)
            return;

        L3CacheStoragePolicyCustomizer customizer = null;
        try {
            customizer = applicationStartedEvent.getApplicationContext().getBean(L3CacheStoragePolicyCustomizer.class);
        } catch (Exception e) {
        }

        L3CacheResolver resolver;

        if (customizer != null && customizer.customize() != null) {
            final L3CacheStoragePolicy storagePolicy = customizer.customize();
            resolver = () -> storagePolicy;
        } else {
            try {
                final L3CacheStoragePolicy storagePolicy = applicationStartedEvent.getApplicationContext().getBean(L3CacheStoragePolicy.class);
                resolver = () -> storagePolicy;
            } catch (Exception e) {
                resolver = () -> null;
            }
        }

        bean.setResolver(resolver);

    }

    private void customizeL3CacheArgsToString(ApplicationStartedEvent applicationStartedEvent) {

        try {
            L3CacheAspect bean = applicationStartedEvent.getApplicationContext().getBean(L3CacheAspect.class);
            if (bean == null)
                return;

            L3CacheArgsToStringCustomizer customizer = null;
            try {
                customizer = applicationStartedEvent.getApplicationContext().getBean(L3CacheArgsToStringCustomizer.class);
            } catch (Exception e) {

            }

            ArgsToString argsToString = null;
            if (customizer == null) {
                argsToString = new DefaultArgsToString();
            } else {
                argsToString = customizer.customize();
            }
            bean.setArgsToString(argsToString);
        } catch (Exception e) {

        }

    }


    private void customizeDataTransform(ApplicationStartedEvent applicationStartedEvent) {
        DataTransformCustomizer customizer = null;
        try {
            customizer = applicationStartedEvent.getApplicationContext().getBean(DataTransformCustomizer.class);
        } catch (Exception e) {

        }

        if (customizer == null)
            return;

        DataTransform dataTransform = customizer.customize();
        if (dataTransform == null)
            return;

        Repository repository = applicationStartedEvent.getApplicationContext().getBean(Repository.class);
        if (repository == null)
            return;
        ((CacheableRepository) repository).setDataTransform(dataTransform);
    }

    private void customizeCacheStoragePolicy(ApplicationStartedEvent applicationStartedEvent) {

        L2CacheStoragePolicyCustomizer customizer = null;
        try {
            customizer = applicationStartedEvent.getApplicationContext().getBean(L2CacheStoragePolicyCustomizer.class);
        } catch (Exception e) {

        }

        L2CacheStoragePolicy cacheStoragePolicy = null;
        if (customizer != null && customizer.customize() != null) {
            cacheStoragePolicy = customizer.customize();
        } else {
            try {
                cacheStoragePolicy = applicationStartedEvent.getApplicationContext().getBean(L2CacheStoragePolicy.class);
            } catch (Exception e) {

            }
        }

        CacheResolver levelTwoCacheResolver = applicationStartedEvent.getApplicationContext().getBean(CacheResolver.class);
        if (levelTwoCacheResolver == null)
            return;
        ((DefaultL2CacheResolver) levelTwoCacheResolver).setCacheStoragePolicy(cacheStoragePolicy);

    }


    private void customizeIdGeneratorPolicy(ApplicationStartedEvent applicationStartedEvent) {
        IdGeneratorPolicyCustomizer customizer = null;
        try {
            customizer = applicationStartedEvent.getApplicationContext().getBean(IdGeneratorPolicyCustomizer.class);
        } catch (Exception e) {

        }

        IdGeneratorPolicy idGeneratorPolicy = null;
        if (customizer != null && customizer.customize() != null) {
            idGeneratorPolicy = customizer.customize();
        }else{
            try {
                idGeneratorPolicy = applicationStartedEvent.getApplicationContext().getBean(IdGeneratorPolicy.class);
            }catch (Exception e){

            }
        }

        if (idGeneratorPolicy == null)
            return;

        IdGeneratorService service = applicationStartedEvent.getApplicationContext().getBean(IdGeneratorService.class);
        if (service == null)
            return;
        service.setIdGeneratorPolicy(idGeneratorPolicy);

    }


    private void transform(ApplicationStartedEvent applicationStartedEvent) {
        List<Class<? extends BaseRepository>> clzzList = null;
        if (SchemaConfig.isSchemaTransformEnabled) {
            clzzList = customizeSchemaTransform(applicationStartedEvent);
        }

        if (clzzList != null) {

            for (Class<? extends BaseRepository> clzz : clzzList) {

                Repository depository = applicationStartedEvent.getApplicationContext().getBean(Repository.class);

                List list = list(depository, clzz);//查出所有配置
                if (!list.isEmpty()) {
                    reparse(list);
                }
            }
        }
    }


    private List<Class<? extends BaseRepository>> customizeSchemaTransform(ApplicationStartedEvent applicationStartedEvent) {


        SchemaTransformCustomizer customizer = null;
        try {
            customizer = applicationStartedEvent.getApplicationContext().getBean(SchemaTransformCustomizer.class);
        } catch (Exception e) {
        }

        if (customizer != null) {
            SchemaTransformRepositoryBuilder builder = new SchemaTransformRepositoryBuilder();
            return customizer.customize(builder);
        }

        SchemaTransformRepositoryBuilder.registry = null;

        List<Class<? extends BaseRepository>> list = new ArrayList<>();
        list.add(SchemaTransformRepository.class);
        return list;
    }


    private void reparse(List list) {

        //key: originTable
        Map<String, List<TransformConfigurable>> map = new HashMap<>();

        for (Object obj : list) {
            if (obj instanceof TransformConfigurable) {

                TransformConfigurable transformed = (TransformConfigurable) obj;
                String originTable = transformed.getOriginTable();
                List<TransformConfigurable> transformedList = map.get(originTable);
                if (transformedList == null) {
                    transformedList = new ArrayList<>();
                    map.put(originTable, transformedList);
                }
                transformedList.add(transformed);
            }
        }

        for (Map.Entry<String, List<TransformConfigurable>> entry : map.entrySet()) {
            String originTable = entry.getKey();

            Parsed parsed = Parser.getByTableName(originTable);
            if (parsed == null)
                continue;

            List<TransformConfigurable> transformedList = entry.getValue();
            for (TransformConfigurable transformed : transformedList) {
                parsed.setTableName(transformed.getTargetTable());//FIXME 直接替换了原始的表
                parsed.setTransforemedAlia(transformed.getAlia());

                for (BeanElement be : parsed.getBeanElementList()) {
                    if (be.getMapper().equals(transformed.getOriginColumn())) {
                        be.mapper = transformed.getTargetColumn();//FIXME 直接替换了原始的列, 只需要目标对象的属性有值
                        break;
                    }
                }
            }

            parsed.reset(parsed.getBeanElementList());
            String tableName = parsed.getTableName();
            Parsed parsedTransformed = Parser.getByTableName(tableName);
            parsed.setParsedTransformed(parsedTransformed);

            SchemaConfig.transformableSet.add(parsed.getClz());

            Map<String, String> sqlMap = MapperFactory.getSqlMap(parsedTransformed.getClz());
            MapperFactory.putSqlMap(parsed.getClz(), sqlMap);
        }
    }

    private List list(Repository dataRepository, Class<? extends BaseRepository> clzz) {

        Type[] types = clzz.getGenericInterfaces();

        ParameterizedType parameterized = (ParameterizedType) types[0];
        Class clazz = (Class) parameterized.getActualTypeArguments()[0];

        Object obj = null;
        try {
            obj = clazz.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        List list = dataRepository.list(obj);

        return list;
    }

}