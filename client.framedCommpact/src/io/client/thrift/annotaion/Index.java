package io.client.thrift.annotaion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模型类字段的顺序
 * <p>
 * Thrift 读写时按照配置的顺序依次进行
 * 
 * @author HouKangxi
 * @date 2014年11月1日
 * 
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {
	int value();
}