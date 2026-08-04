package org.cmb.teamcoordinator.persistence;

@FunctionalInterface
public interface MyBatisRowMapper<T> {

    T mapRow(MyBatisRow row, int rowNumber) throws Exception;
}
