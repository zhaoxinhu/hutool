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

package org.dromara.hutool.core.io.watch;

import org.dromara.hutool.core.io.IoUtil;
import org.dromara.hutool.core.func.SerBiConsumer;
import org.dromara.hutool.core.array.ArrayUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 文件监听服务，此服务可以同时监听多个路径。
 *
 * @author loolly
 * @since 5.1.0
 */
public class WatchServer extends Thread implements Closeable, Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 监听服务
	 */
	private WatchService watchService;
	/**
	 * 监听事件列表
	 */
	protected WatchEvent.Kind<?>[] events;
	/**
	 * 监听选项，例如监听频率等
	 */
	private WatchEvent.Modifier[] modifiers;
	/**
	 * 监听是否已经关闭
	 */
	protected boolean isClosed;
	/**
	 * WatchKey 和 Path的对应表
	 */
	private final Map<WatchKey, Path> watchKeyPathMap = new HashMap<>();

	/**
	 * 初始化<br>
	 * 初始化包括：
	 * <pre>
	 * 1、解析传入的路径，判断其为目录还是文件
	 * 2、创建{@link WatchService} 对象
	 * </pre>
	 *
	 * @throws WatchException 监听异常，IO异常时抛出此异常
	 */
	public void init() throws WatchException {
		//初始化监听
		try {
			watchService = FileSystems.getDefault().newWatchService();
		} catch (final IOException e) {
			throw new WatchException(e);
		}

		isClosed = false;
	}

	/**
	 * 设置监听选项，例如监听频率等，可设置项包括：
	 *
	 * <pre>
	 * 1、com.sun.nio.file.StandardWatchEventKinds
	 * 2、com.sun.nio.file.SensitivityWatchEventModifier
	 * </pre>
	 *
	 * @param modifiers 监听选项，例如监听频率等
	 */
	public void setModifiers(final WatchEvent.Modifier[] modifiers) {
		this.modifiers = modifiers;
	}

	/**
	 * 将指定路径加入到监听中
	 *
	 * @param path     路径
	 * @param maxDepth 递归下层目录的最大深度
	 */
	public void registerPath(final Path path, final int maxDepth) {
		final WatchEvent.Kind<?>[] kinds = ArrayUtil.defaultIfEmpty(this.events, WatchKind.ALL);

		try {
			final WatchKey key;
			if (ArrayUtil.isEmpty(this.modifiers)) {
				key = path.register(this.watchService, kinds);
			} else {
				key = path.register(this.watchService, kinds, this.modifiers);
			}
			watchKeyPathMap.put(key, path);

			// 递归注册下一层层级的目录
			if (maxDepth > 1) {
				//遍历所有子目录并加入监听
				Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
						registerPath(dir, 0);//继续添加目录
						return super.postVisitDirectory(dir, exc);
					}
				});
			}
		} catch (final IOException e) {
			if (! (e instanceof AccessDeniedException)) {
				throw new WatchException(e);
			}

			//对于禁止访问的目录，跳过监听
		}
	}

	/**
	 * 执行事件获取并处理
	 *
	 * @param action     监听回调函数，实现此函数接口用于处理WatchEvent事件
	 * @param watchFilter 监听过滤接口，通过实现此接口过滤掉不需要监听的情况，{@link Predicate#test(Object)}为{@code true}保留，null表示不过滤
	 * @since 5.4.0
	 */
	public void watch(final SerBiConsumer<WatchEvent<?>, Path> action, final Predicate<WatchEvent<?>> watchFilter) {
		final WatchKey wk;
		try {
			wk = watchService.take();
		} catch (final InterruptedException | ClosedWatchServiceException e) {
			// 用户中断
			close();
			return;
		}

		final Path currentPath = watchKeyPathMap.get(wk);

		for (final WatchEvent<?> event : wk.pollEvents()) {
			// 如果监听文件，检查当前事件是否与所监听文件关联
			if (null != watchFilter && ! watchFilter.test(event)) {
				continue;
			}

			action.accept(event, currentPath);
		}

		wk.reset();
	}

	/**
	 * 执行事件获取并处理
	 *
	 * @param watcher     {@link Watcher}
	 * @param watchFilter 监听过滤接口，通过实现此接口过滤掉不需要监听的情况，{@link Predicate#test(Object)}为{@code true}保留，null表示不过滤
	 */
	public void watch(final Watcher watcher, final Predicate<WatchEvent<?>> watchFilter) {
		watch((event, currentPath)->{
			final WatchEvent.Kind<?> kind = event.kind();

			if (kind == WatchKind.CREATE.getValue()) {
				watcher.onCreate(event, currentPath);
			} else if (kind == WatchKind.MODIFY.getValue()) {
				watcher.onModify(event, currentPath);
			} else if (kind == WatchKind.DELETE.getValue()) {
				watcher.onDelete(event, currentPath);
			} else if (kind == WatchKind.OVERFLOW.getValue()) {
				watcher.onOverflow(event, currentPath);
			}
		}, watchFilter);
	}

	/**
	 * 关闭监听
	 */
	@Override
	public void close() {
		isClosed = true;
		IoUtil.closeQuietly(watchService);
	}
}