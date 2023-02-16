package com.example.productorderservice;

import com.google.common.base.CaseFormat;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Table;
import javax.persistence.metamodel.EntityType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DatabaseCleanup implements InitializingBean {

    @PersistenceContext
    private EntityManager entityManager;

    private List<String> tableNames;

    @Override
    public void afterPropertiesSet(){
        // EntityManager에서 Entity들을 모두 가져온다.
        final Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();

        // 가져온 Entity들을 차례대로 돌면서 스트림을 통하여
        tableNames = entities.stream()
                .filter(e -> isEntity(e) && hasTableAnnotation(e)) // 돌고있는 Entity의 자바타입에서 Entity라는 Annotation이 있는지 확인한다.
                .map(e -> e.getJavaType().getAnnotation(Table.class).name()) // 그 다음에 Table이라는 어노테이션이 있는지 확인한다.
                .collect(Collectors.toList()); // 위 조건을 모두 만족하는 테이블의 이름을 List에 담는다.

        // 테이블 이름을 가져온 후에
        final List<String> entityNames = entities.stream()
                .filter(e -> isEntity(e) && !hasTableAnnotation(e)) // Entity어노테이션은 있지만 Table어노테이션이 없는 애들
                .map(e -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, e.getName()))
                .toList();
        // CaseFormat은 구글에서 제공하는 라이브러리 기능 중 하나로 예를들면면ProductItem 이라는 단어를 product_item 이라는 단어로 변경해준다.
        // 인텔리제이 상단메뉴의 View-> Tool Windows -> Dependencies 를 열어서 guava를 검색하여 추가한다.

        tableNames.addAll(entityNames);
    }

    private boolean isEntity(final EntityType<?> e) {
        return null != e.getJavaType().getAnnotation(Entity.class);
    }

    private boolean hasTableAnnotation(final EntityType<?> e) {
        return null != e.getJavaType().getAnnotation(Table.class);
    }

    @Transactional
    public void excute(){
        entityManager.flush();

        // 참조무결성을 무시하는 명령, PK와 FK로 묶여 있기에 안지워지는 등의 데이터들을 삭제할 때 유용함.
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        for(final String tableName : tableNames){
            // 위에서 만든 테이블 목록을 TRUNCATE 한다.
            entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();

            //  @GeneratedValue 로 셋팅된 시퀀스를 초기화 한다.
            entityManager.createNativeQuery("ALTER TABLE " + tableName + " ALTER COLUMN ID RESTART WITH 1").executeUpdate();
        }

        // 다시 참조무결성을 체크하도록 셋팅해준다.
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
