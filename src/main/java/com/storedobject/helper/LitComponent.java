/*
 *  Copyright 2019-2020 Syam Pillai
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.storedobject.helper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.littemplate.LitTemplate;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.function.SerializableConsumer;

import elemental.json.JsonValue;

/**
 * <p>
 * This is a simple base component to create a Vaadin component from a LitElement that supports easy execution of
 * Javascript. Normally, execution of Javascript is possible only when the front-end part of the component is attached
 * to the DOM tree and in a ready-state. Vaadin makes sure that the JS commands are executed only when the component is
 * attached. However, there is no guarantee that the component is still in a ready state at that time because of the
 * complexity of the logic in the component.
 * </p>
 * <p>
 * This class caches all the commands till the front-end is ready to receive commands. The LitElement (client-side)
 * implementation should invoke the server-side {@link #ready()} method from the connectedCallback() {
 * super.connectedCallback(); this.$server.ready(); } or from any other suitable place when the element is fully ready
 * to receive updates from the server side. If you are using TS instead of JS, please make sure that you cast the "this"
 * as "any" to comply with the strong typing of TS - (this as any).$server.ready();
 * </p>
 * <p>
 * Since this component is extended from Vaadin's {@link LitTemplate}, it has all the features supported by that class
 * too.
 * </p>
 *
 * @author Syam
 */
public abstract class LitComponent extends LitTemplate {

	private final List<JSFunction> functions = new ArrayList<>();
	private volatile boolean ready = false;

	@ClientCallable
	private void ready() {
		synchronized (functions) {
			JSFunction function;
			while (!functions.isEmpty()) {
				function = functions.remove(0);
				final JSResult r = (JSResult) function.result;
				final PendingJavaScriptResult pr = exec(function.function, function.parameters);
				pr.then(r.successConsumer, r.failureConsumer);
				function.result = pr;
			}
			ready = true;
		}
	}

	/**
	 * Execute Javascript function with the parameter passed. Execution will be delayed till the front-end is ready.
	 *
	 * @param function
	 *            Javascript function to execute,
	 * @param parameters
	 *            Parameters of the function.
	 * @return Result details.
	 */
	@SuppressWarnings("UnusedReturnValue")
	protected synchronized PendingJavaScriptResult executeJS(final String function, final Serializable... parameters) {
		if (ready) {
			return exec(function, parameters);
		} else {
			synchronized (functions) {
				if (ready) {
					return exec(function, parameters);
				} else {
					final JSFunction jsFunction = new JSFunction(function, parameters);
					functions.add(jsFunction);
					jsFunction.result = new JSResult(jsFunction);
					return jsFunction.result;
				}
			}
		}
	}

	private PendingJavaScriptResult exec(final String function, final Serializable... parameters) {
		return getElement().callJsFunction(function, parameters);
	}

	@Override
	protected void onDetach(final DetachEvent detachEvent) {
		ready = false;
		super.onDetach(detachEvent);
	}

	private class JSResult implements PendingJavaScriptResult {

		private final JSFunction function;
		private SerializableConsumer<JsonValue> successConsumer = j -> {
		};
		private SerializableConsumer<String> failureConsumer = s -> {
		};

		private JSResult(final JSFunction function) {
			this.function = function;
		}

		@Override
		public boolean cancelExecution() {
			if (ready) {
				return function.result.cancelExecution();
			}
			synchronized (functions) {
				if (ready) {
					return function.result.cancelExecution();
				}
				return functions.removeIf(r -> r.result == this);
			}
		}

		@Override
		public boolean isSentToBrowser() {
			if (ready) {
				return function.result.isSentToBrowser();
			}
			synchronized (functions) {
				return ready && function.result.isSentToBrowser();
			}
		}

		@Override
		public void then(final SerializableConsumer<JsonValue> successConsumer,
				final SerializableConsumer<String> failureConsumer) {
			if (successConsumer != null) {
				this.successConsumer = successConsumer;
			}
			if (failureConsumer != null) {
				this.failureConsumer = failureConsumer;
			}
		}
	}

	private static class JSFunction {

		private final String function;
		private final Serializable[] parameters;
		private PendingJavaScriptResult result;

		private JSFunction(final String function, final Serializable[] parameters) {
			this.function = function;
			this.parameters = parameters;
		}
	}
}
