/*
 * Copyright (c) 2023 looly(loolly@aliyun.com)
 * Hutool is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */

package org.dromara.hutool.core.classloader;

import org.dromara.hutool.core.exceptions.UtilException;
import org.dromara.hutool.core.lang.Assert;
import org.dromara.hutool.core.map.BiMap;
import org.dromara.hutool.core.reflect.ClassUtil;
import org.dromara.hutool.core.text.StrTrimer;
import org.dromara.hutool.core.text.StrUtil;
import org.dromara.hutool.core.util.CharUtil;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;

/**
 * 类描述工具类<br>
 * 来自：org.apache.dubbo.common.utils.ReflectUtils
 *
 * @author Dubbo
 * @since 6.0.0
 */
public class ClassDescUtil {
	/**
	 * void(V).
	 */
	public static final char JVM_VOID = 'V';

	/**
	 * boolean(Z).
	 */
	public static final char JVM_BOOLEAN = 'Z';

	/**
	 * byte(B).
	 */
	public static final char JVM_BYTE = 'B';

	/**
	 * char(C).
	 */
	public static final char JVM_CHAR = 'C';

	/**
	 * double(D).
	 */
	public static final char JVM_DOUBLE = 'D';

	/**
	 * float(F).
	 */
	public static final char JVM_FLOAT = 'F';

	/**
	 * int(I).
	 */
	public static final char JVM_INT = 'I';

	/**
	 * long(J).
	 */
	public static final char JVM_LONG = 'J';

	/**
	 * short(S).
	 */
	public static final char JVM_SHORT = 'S';

	/**
	 * 原始类型名和其class对应表，例如：int.class 《=》 int
	 */
	private static final BiMap<Class<?>, Character> PRIMITIVE_CLASS_DESC_MAP = new BiMap<>(new HashMap<>(9, 1));
	private static final BiMap<Class<?>, String> PRIMITIVE_CLASS_NAME_MAP = new BiMap<>(new HashMap<>(9, 1));

	static {
		PRIMITIVE_CLASS_DESC_MAP.put(void.class, JVM_VOID);
		PRIMITIVE_CLASS_NAME_MAP.put(void.class, "void");
		PRIMITIVE_CLASS_DESC_MAP.put(boolean.class, JVM_BOOLEAN);
		PRIMITIVE_CLASS_NAME_MAP.put(boolean.class, "boolean");
		PRIMITIVE_CLASS_DESC_MAP.put(byte.class, JVM_BYTE);
		PRIMITIVE_CLASS_NAME_MAP.put(byte.class, "byte");
		PRIMITIVE_CLASS_DESC_MAP.put(char.class, JVM_CHAR);
		PRIMITIVE_CLASS_NAME_MAP.put(char.class, "char");
		PRIMITIVE_CLASS_DESC_MAP.put(double.class, JVM_DOUBLE);
		PRIMITIVE_CLASS_NAME_MAP.put(double.class, "double");
		PRIMITIVE_CLASS_DESC_MAP.put(float.class, JVM_FLOAT);
		PRIMITIVE_CLASS_NAME_MAP.put(float.class, "float");
		PRIMITIVE_CLASS_DESC_MAP.put(int.class, JVM_INT);
		PRIMITIVE_CLASS_NAME_MAP.put(int.class, "int");
		PRIMITIVE_CLASS_DESC_MAP.put(long.class, JVM_LONG);
		PRIMITIVE_CLASS_NAME_MAP.put(long.class, "long");
		PRIMITIVE_CLASS_DESC_MAP.put(short.class, JVM_SHORT);
		PRIMITIVE_CLASS_NAME_MAP.put(short.class, "short");
	}

	/**
	 * Class描述转Class
	 * <pre>{@code
	 * "[Z" => boolean[].class
	 * "[[Ljava/util/Map;" => java.util.Map[][].class
	 * }</pre>
	 *
	 * @param desc 类描述
	 * @return Class
	 * @throws UtilException 类没有找到
	 */
	public static Class<?> descToClass(final String desc) throws UtilException {
		return descToClass(desc, true, null);
	}

	/**
	 * Class描述转Class
	 * <pre>{@code
	 * "[Z" => boolean[].class
	 * "[[Ljava/util/Map;" => java.util.Map[][].class
	 * }</pre>
	 *
	 * @param desc          类描述
	 * @param isInitialized 是否初始化类
	 * @param cl            {@link ClassLoader}
	 * @return Class
	 * @throws UtilException 类没有找到
	 */
	public static Class<?> descToClass(String desc, final boolean isInitialized, final ClassLoader cl) throws UtilException {
		final char firstChar = desc.charAt(0);
		final Class<?> clazz = PRIMITIVE_CLASS_DESC_MAP.getKey(firstChar);
		if (null != clazz) {
			return clazz;
		}
		switch (firstChar) {
			case 'L':
				// "Ljava/lang/Object;" ==> "java.lang.Object"
				desc = desc.substring(1, desc.length() - 1).replace('/', '.');
				break;
			case '[':
				// "[[Ljava/lang/Object;" ==> "[[Ljava.lang.Object;"
				desc = desc.replace(CharUtil.SLASH, CharUtil.DOT);
				break;
			default:
				throw new UtilException("Class not found for : " + desc);
		}

		return ClassUtil.forName(desc, isInitialized, cl);
	}

	/**
	 * get class desc.
	 * boolean[].class => "[Z"
	 * Object.class => "Ljava/lang/Object;"
	 *
	 * @param c class.
	 * @return desc.
	 */
	public static String getDesc(Class<?> c) {
		final StringBuilder ret = new StringBuilder();

		while (c.isArray()) {
			ret.append('[');
			c = c.getComponentType();
		}

		if (c.isPrimitive()) {
			final Character desc = PRIMITIVE_CLASS_DESC_MAP.get(c);
			if (null != desc) {
				ret.append(desc.charValue());
			}
		} else {
			ret.append('L');
			ret.append(c.getName().replace('.', '/'));
			ret.append(';');
		}
		return ret.toString();
	}

	/**
	 * 获取方法或构造描述<br>
	 * 方法：
	 * <pre>{@code
	 * int do(int arg1) => "do(I)I"
	 * void do(String arg1,boolean arg2) => "do(Ljava/lang/String;Z)V"
	 * }</pre>
	 * 构造：
	 * <pre>
	 * "()V", "(Ljava/lang/String;I)V"
	 * </pre>
	 *
	 * @param methodOrConstructor 方法或构造
	 * @return 描述
	 */
	public static String getDesc(final Executable methodOrConstructor) {
		final StringBuilder ret = new StringBuilder();
		if (methodOrConstructor instanceof Method) {
			ret.append(methodOrConstructor.getName());
		}
		ret.append('(');

		// 参数
		final Class<?>[] parameterTypes = methodOrConstructor.getParameterTypes();
		for (final Class<?> parameterType : parameterTypes) {
			ret.append(getDesc(parameterType));
		}

		// 返回类型或构造标记
		ret.append(')');
		if (methodOrConstructor instanceof Method) {
			ret.append(getDesc(((Method) methodOrConstructor).getReturnType()));
		} else {
			ret.append('V');
		}

		return ret.toString();
	}

	/**
	 * 获取code base
	 *
	 * @param clazz 类
	 * @return code base
	 */
	public static String getCodeBase(final Class<?> clazz) {
		if (clazz == null) {
			return null;
		}
		final ProtectionDomain domain = clazz.getProtectionDomain();
		if (domain == null) {
			return null;
		}
		final CodeSource source = domain.getCodeSource();
		if (source == null) {
			return null;
		}
		final URL location = source.getLocation();
		if (location == null) {
			return null;
		}
		return location.getFile();
	}

	/**
	 * name to class.
	 * "boolean" => boolean.class
	 * "java.util.Map[][]" => java.util.Map[][].class
	 *
	 * @param name          name.
	 * @param isInitialized 是否初始化类
	 * @param cl            ClassLoader instance.
	 * @return Class instance.
	 */
	public static Class<?> nameToClass(String name, final boolean isInitialized, final ClassLoader cl) {
		Assert.notNull(name, "Name must not be null");
		// 去除尾部多余的"."和"/"
		name = StrUtil.trim(name, StrTrimer.TrimMode.SUFFIX, (c) ->
			CharUtil.SLASH == c || CharUtil.DOT == c);

		int c = 0;
		final int index = name.indexOf('[');
		if (index > 0) {
			c = (name.length() - index) / 2;
			name = name.substring(0, index);
		}

		if (c > 0) {
			final StringBuilder sb = new StringBuilder();
			while (c-- > 0) {
				sb.append('[');
			}

			final Class<?> clazz = PRIMITIVE_CLASS_NAME_MAP.getKey(name);
			if (null != clazz) {
				// 原始类型数组，根据name获取其描述
				sb.append(PRIMITIVE_CLASS_DESC_MAP.get(clazz).charValue());
			} else {
				// 对象数组
				// "java.lang.Object" ==> "Ljava.lang.Object;"
				sb.append('L').append(name).append(';');
			}
			name = sb.toString();
		} else {
			final Class<?> clazz = PRIMITIVE_CLASS_NAME_MAP.getKey(name);
			if (null != clazz) {
				return clazz;
			}
		}

		return ClassUtil.forName(name.replace(CharUtil.SLASH, CharUtil.DOT), isInitialized, cl);
	}

	/**
	 * 类名称转描述
	 *
	 * <pre>{@code
	 * java.util.Map[][] => "[[Ljava/util/Map;"
	 * }</pre>
	 *
	 * @param name 名称
	 * @return 描述
	 */
	public static String nameToDesc(String name) {
		final StringBuilder sb = new StringBuilder();
		int c = 0;
		final int index = name.indexOf('[');
		if (index > 0) {
			c = (name.length() - index) / 2;
			name = name.substring(0, index);
		}
		while (c-- > 0) {
			sb.append('[');
		}

		final Class<?> clazz = PRIMITIVE_CLASS_NAME_MAP.getKey(name);
		if (null != clazz) {
			// 原始类型数组，根据name获取其描述
			sb.append(PRIMITIVE_CLASS_DESC_MAP.get(clazz).charValue());
		} else {
			// 对象数组
			// "java.lang.Object" ==> "Ljava.lang.Object;"
			sb.append('L').append(name.replace(CharUtil.DOT, CharUtil.SLASH)).append(';');
		}

		return sb.toString();
	}

	/**
	 * 类描述转名称
	 *
	 * <pre>{@code
	 * "[[I" => "int[][]"
	 * }</pre>
	 *
	 * @param desc 描述
	 * @return 名称
	 */
	public static String descToName(final String desc) {
		final StringBuilder sb = new StringBuilder();
		int c = desc.lastIndexOf('[') + 1;
		if (desc.length() == c + 1) {
			final char descChar = desc.charAt(c);
			final Class<?> clazz = PRIMITIVE_CLASS_DESC_MAP.getKey(descChar);
			if (null != clazz) {
				sb.append(PRIMITIVE_CLASS_NAME_MAP.get(clazz));
			} else {
				throw new UtilException("Unsupported primitive desc: {}", desc);
			}
		} else {
			sb.append(desc.substring(c + 1, desc.length() - 1).replace(CharUtil.SLASH, CharUtil.DOT));
		}
		while (c-- > 0) {
			sb.append("[]");
		}
		return sb.toString();
	}
}