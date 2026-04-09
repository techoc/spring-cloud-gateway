/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.event;

import org.springframework.context.ApplicationEvent;

/**
 * 路由刷新结果事件。
 *
 * <p>
 * 在 {@link RefreshRoutesEvent} 触发的路由刷新操作完成后发布此事件， 用于通知订阅者刷新操作是成功还是失败。
 * </p>
 *
 * <p>
 * 该事件携带刷新过程中可能发生的异常信息：
 * </p>
 * <ul>
 * <li>若 {@link #getThrowable()} 返回 {@code null}，表示路由刷新成功</li>
 * <li>若异常不为空，可通过 {@link #isSuccess()} 判断（返回 false）， 并通过 {@link #getThrowable()}
 * 获取具体异常堆栈</li>
 * </ul>
 *
 * <p>
 * 典型使用场景：
 * </p>
 * <ul>
 * <li>监听刷新结果，发送告警通知（如刷新失败时通知运维）</li>
 * <li>记录刷新操作的执行日志</li>
 * <li>配合健康检查指标上报刷新状态</li>
 * </ul>
 *
 * @author alvin
 * @see RefreshRoutesEvent
 * @see #isSuccess()
 * @see #getThrowable()
 */
public class RefreshRoutesResultEvent extends ApplicationEvent {

	/**
	 * 路由刷新过程中发生的异常（如果刷新成功则为 null）。 若不为 null，表明刷新操作未完成或存在问题。
	 */
	private Throwable throwable;

	/**
	 * 构造一个表示刷新失败的路由刷新结果事件。
	 * @param source 事件源对象（通常为发布事件的组件）
	 * @param throwable 刷新过程中捕获的异常（非 null）
	 */
	public RefreshRoutesResultEvent(Object source, Throwable throwable) {
		super(source);
		this.throwable = throwable;
	}

	/**
	 * 构造一个表示刷新成功的路由刷新结果事件。
	 * @param source 事件源对象（通常为发布事件的组件）
	 */
	public RefreshRoutesResultEvent(Object source) {
		super(source);
	}

	/**
	 * 获取路由刷新过程中发生的异常对象。
	 * @return 异常对象，若刷新成功则为 null
	 */
	public Throwable getThrowable() {
		return throwable;
	}

	/**
	 * 判断路由刷新操作是否成功。
	 * @return 刷新成功返回 true；刷新失败（存在异常）返回 false
	 */
	public boolean isSuccess() {
		return throwable == null;
	}

}
