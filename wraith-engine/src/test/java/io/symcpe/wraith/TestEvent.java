/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.wraith;

import java.util.HashMap;
import java.util.Map;

import io.symcpe.wraith.Event;

/**
 * Dummy event for unit testing
 * 
 * @author ambud_sharma
 */
public class TestEvent implements Event {
	
	private static final long serialVersionUID = 1L;
	private Map<String, Object> headers;
	private byte[] body;

	public TestEvent() {
		this.headers = new HashMap<String, Object>();
	}

	@Override
	public Map<String, Object> getHeaders() {
		return headers;
	}

	@Override
	public byte[] getBody() {
		return body;
	}

	public void setBody(byte[] body) {
		this.body = body;
	}

	@Override
	public void setHeaders(Map<String, Object> headers) {
		this.headers = headers;
	}

}
