package com.github.repository;

import com.github.model.Config;
import com.github.model.Document;
import com.github.model.Relation;
import com.github.model.Scheme;
import com.github.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Component
public class DataRepository {

    private final Config config;
    private final JdbcTemplate jdbcTemplate;
    private final EsRepository esRepository;

    @Autowired
    public DataRepository(Config config, JdbcTemplate jdbcTemplate, EsRepository esRepository) {
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
        this.esRepository = esRepository;
    }

    /** generate scheme of es on the database table structure */
    public List<Scheme> dbToEsScheme() {
        List<Scheme> schemeList = A.lists();
        for (Relation relation : config.getRelation()) {
            List<Map<String, Object>> mapList = jdbcTemplate.queryForList(relation.descSql());
            if (A.isNotEmpty(mapList)) {
                boolean scheme = relation.isScheme();

                List<String> keyList = A.lists();
                Map<String, Map> propertyMap = A.maps();
                Map<String, Boolean> fieldMap = A.maps();
                for (Map<String, Object> map : mapList) {
                    Object column = map.get("Field");
                    Object type = map.get("Type");

                    if (U.isNotBlank(column) && U.isNotBlank(type)) {
                        String field = column.toString();
                        fieldMap.put(field, true);
                        if (scheme) {
                            propertyMap.put(relation.useField(field), Searchs.dbToEsType(type.toString()));
                        }

                        Object key = map.get("Key");
                        if (U.isNotBlank(key) && "PRI".equals(key)) {
                            keyList.add(field);
                        }
                    }
                }

                List<String> keyColumn = relation.getKeyColumn();
                String table = relation.getTable();
                if (A.isEmpty(keyColumn)) {
                    U.assertException(A.isEmpty(keyList),
                            String.format("table (%s) no primary key, can't create index in es!", table));

                    if (keyList.size() > 1) {
                        if (Logs.ROOT_LOG.isWarnEnabled()) {
                            Logs.ROOT_LOG.warn("table ({}) has multi primary key, " +
                                    "increment data may be query for duplicate data!", table);
                        }
                    }
                    relation.setKeyColumn(keyList);
                } else {
                    for (String key : keyColumn) {
                        U.assertNil(fieldMap.get(key), String.format("table (%s) don't have column (%s)", table, key));
                    }
                }
                if (scheme) {
                    schemeList.add(new Scheme().setIndex(relation.useIndex()).setProperties(propertyMap));
                }
            }
        }
        return schemeList;
    }

    /** async data to es */
    @Async
    public Future<Boolean> asyncData(Relation relation) {
        if (A.isEmpty(relation.getKeyColumn())) {
            dbToEsScheme();
        }
        saveData(relation);
        return new AsyncResult<>(true);
    }
    private void saveData(Relation relation) {
        String index = relation.useIndex();
        for (;;) {
            String tmpColumnValue = Files.read(index);
            Integer count = A.first(jdbcTemplate.queryForList(relation.countSql(tmpColumnValue), Integer.class));
            if (U.less0(count)) {
                return;
            } else {
                List<Document> documents = A.lists();
                List<Map<String, Object>> dataList = null;

                int loopCount = relation.loopCount(count);
                int writeCount = 0;
                for (int i = 0; i < loopCount; i++) {
                    dataList = jdbcTemplate.queryForList(relation.querySql(i, tmpColumnValue));

                    documents.addAll(fixDocument(relation, dataList));
                    // batch insert
                    if (documents.size() >= config.getCount()) {
                        esRepository.saveDataToEs(documents);
                        documents.clear();
                        writeCount++;

                        // save count * 20 data to es, then write last in temp file
                        if (writeCount % 20 == 0) {
                            String last = getLast(relation, dataList);
                            if (U.isNotBlank(last)) {
                                Files.write(index, last);
                            }
                        }
                    }
                }

                // save last data
                if (A.isNotEmpty(documents)) {
                    esRepository.saveDataToEs(documents);
                }
                // write last in temp file
                String last = getLast(relation, dataList);
                if (U.isNotBlank(last)) {
                    Files.write(index, last);
                }

                if (count < relation.getLimit()) {
                    return;
                }
            }
        }
    }
    /** return last record */
    private String getLast(Relation relation, List<Map<String, Object>> dataList) {
        Map<String, Object> last = A.last(dataList);
        if (A.isEmpty(last)) {
            return U.EMPTY;
        } else {
            List<String> lastList = A.lists();
            for (String columnAlias : relation.getIncrementColumnAlias()) {
                String obj = dataToStr(last.get(columnAlias));
                if (U.isNotBlank(obj)) {
                    lastList.add(obj);
                }
            }
            return A.toStr(lastList, U.SPLIT);
        }
    }
    private String dataToStr(Object obj) {
        // if was Date return 'yyyy-MM-dd HH:mm:ss', else return toStr
        if (U.isBlank(obj)) {
            return null;
        } else if (obj instanceof Date) {
            return Dates.format((Date) obj, Dates.Type.YYYY_MM_DD_HH_MM_SS);
        } else {
            return obj.toString();
        }
    }
    /** traverse the Database Result and organize into es Document */
    private List<Document> fixDocument(Relation relation, List<Map<String, Object>> dataList) {
        List<Document> documents = A.lists();
        for (Map<String, Object> objMap : dataList) {
            StringBuilder sbd = new StringBuilder();
            String idPrefix = relation.getIdPrefix();
            if (U.isNotBlank(idPrefix)) {
                sbd.append(idPrefix);
            }
            for (String primary : relation.getKeyColumn()) {
                sbd.append(objMap.get(primary)).append("-");
            }
            if (sbd.toString().endsWith("-")) {
                sbd.delete(sbd.length() - 1, sbd.length());
            }
            String idSuffix = relation.getIdSuffix();
            if (U.isNotBlank(idSuffix)) {
                sbd.append(idSuffix);
            }
            String id = sbd.toString();
            // Document no id, can't be save
            if (U.isNotBlank(id)) {
                Map<String, Object> dataMap = A.newHashMap();
                for (Map.Entry<String, Object> entry : objMap.entrySet()) {
                    String key = relation.useField(entry.getKey());
                    if (U.isNotBlank(key)) {
                        Object value = entry.getValue();
                        // field has suggest and null, can't be write => https://elasticsearch.cn/question/4051
                        // dataMap.put(key, U.isBlank(value) ? " " : value);
                        dataMap.put(key, value);
                    }
                }
                // Document no data, don't need to save? or update to nil?
                if (A.isNotEmpty(dataMap)) {
                    documents.add(new Document(relation.useIndex(), id, dataMap));
                }
            }
        }
        return documents;
    }

    public void deleteTempFile() {
        for (Relation relation : config.getRelation()) {
            Files.delete(relation.useIndex());
        }
    }
}
