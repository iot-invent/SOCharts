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

package com.storedobject.chart;

/**
 * Represents the symbol used to draw a point on the chart. (Example usage is in {@link LineChart}).
 *
 * @author Syam
 */
public class PointSymbol implements ComponentProperty {
	private PointSymbolType type = PointSymbolType.CIRCLE;
	private boolean show = true;
	String size;
	private boolean hoverAnimation = true;
	private String url;
	private String svgPath;
	private Boolean isURL = true;

	public PointSymbol() {
		url = "";
		svgPath = "";
	}

	public void setUrl(final String url) {
		this.setType(PointSymbolType.NONE);
		isURL = true;
		this.url = String.format("image://%s", url);
	}

	public String getCustomSymbol() {
		return isURL ? url : svgPath;
	}

	public void setSvgPath(final String svgPath) {
		this.setType(PointSymbolType.NONE);
		isURL = false;
		this.svgPath = String.format("path://%s", svgPath);
	}

	public void show() {
		show = true;
	}

	public void hide() {
		show = false;
	}

	public void setType(final PointSymbolType pointSymbolType) {
		type = pointSymbolType;
	}

	public void setSize(final int size) {
		if (size <= 0) {
			this.size = null;
		} else {
			this.size = "" + size;
		}
	}

	public void setSize(final String size) {
		this.size = size;
	}

	public void setSize(final int width, final int height) {
		if (width > 0 && height > 0) {
			size = "[" + width + "," + height + "]";
		} else if (width > 0) {
			setSize(width);
		} else if (height > 0) {
			setSize(height);
		} else {
			size = null;
		}
	}

	public void setHoverAnimation(final boolean hoverAnimation) {
		this.hoverAnimation = hoverAnimation;
	}

	@Override
    public void encodeJSON(StringBuilder sb) {
        String t = this.type != PointSymbolType.NONE ? this.type.toString() : this.getCustomSymbol();
		ComponentPart.encode(sb, "showSymbol", show);
		ComponentPart.encode(sb, "symbol", t);
		if (size != null) {
			ComponentPart.addComma(sb);
            sb.append("\"symbolSize\":").append(size);
		}
		ComponentPart.encode(sb, "hoverAnimation", hoverAnimation);
	}
}
