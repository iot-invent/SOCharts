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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents abstract "data zoom" component. Data zoom components allow the end-users to zoom in and to zoom out charts
 * using mouse and/or touch devices.
 *
 * @author Syam
 */
public abstract class AbstractDataZoom extends AbstractPart implements Component {

	private final String type;
	private final List<Axis> axes = new ArrayList<>();
	private final CoordinateSystem coordinateSystem;
	private int filterMode = Integer.MAX_VALUE;
	private int start = Integer.MIN_VALUE, end = Integer.MAX_VALUE;
	private Object startValue, endValue;
	private int minSpan = Integer.MIN_VALUE, maxSpan = Integer.MAX_VALUE;
	private Object minSpanValue, maxSpanValue;
	private boolean zoomLock;
	private boolean showDetail = true;

	/**
	 * Constructor.
	 *
	 * @param type
	 *            Type.
	 * @param coordinateSystem
	 *            Coordinate system.
	 * @param axes
	 *            Axis list.
	 */
	AbstractDataZoom(final String type, final CoordinateSystem coordinateSystem, final Axis... axes) {
		this.type = type;
		this.coordinateSystem = coordinateSystem;
		addAxis(axes);
	}

	/**
	 * Add list of axes.
	 *
	 * @param axes
	 *            Axis list.
	 */
	public void addAxis(final Axis... axes) {
		if (axes != null) {
			for (final Axis a : axes) {
				if (a != null && !this.axes.contains(a)) {
					this.axes.add(a);
				}
			}
		}
	}

	@Override
	public void validate() throws ChartException {
		if (coordinateSystem.getSerial() < 0) {
			throw new ChartException("Coordinate system is not used");
		}
		if (axes.isEmpty()) {
			axes.addAll(coordinateSystem.axes);
			return;
		}
		for (final Axis a : axes) {
			if (!coordinateSystem.axes.contains(a)) {
				String name = a.getName();
				if (name == null) {
					name = ComponentPart.className(a.getClass());
				}
				throw new ChartException("Axis " + name + " doesn't belong to the coordinate system of this");
			}
		}
	}

	@Override
	public void encodeJSON(final StringBuilder sb) {
		super.encodeJSON(sb);
		ComponentPart.encode(sb, "type", type);
		final Set<Class<?>> axisClasses = new HashSet<>();
		axes.forEach(a -> axisClasses.add(a.getClass()));
		axisClasses.forEach(ac -> {
			final AtomicBoolean first = new AtomicBoolean(true);
			axes.stream().filter(a -> a.getClass() == ac).forEach(a -> {
				if (first.get()) {
					first.set(false);
					sb.append(",\"").append(a.axisName()).append("Index\":[");
				} else {
					sb.append(',');
				}
				sb.append(a.wrap(coordinateSystem).getSerial());
			});
			sb.append(']');
		});
		if (filterMode != Integer.MAX_VALUE) {
			sb.append(",\"filterMode\":\"");
			switch (filterMode) {
			case 0:
				sb.append("none");
				break;
			case 1:
				sb.append("empty");
				break;
			case 2:
				sb.append("weakFilter");
				break;
			case 3: {
				sb.append("filter");
				filterMode = Integer.MAX_VALUE;
				break;
			}
			}
			sb.append("\"");
		}
		if (start != Integer.MIN_VALUE || end != Integer.MAX_VALUE || startValue != null || endValue != null) {
			sb.append(",\"rangeMode\":[\"");
			sb.append(startValue != null ? "value" : "percent");
			sb.append("\",\"");
			sb.append(endValue != null ? "value" : "percent");
			sb.append("\"]");
		}
		if (start != Integer.MIN_VALUE) {
			sb.append(",\"start\":").append(start);
			if (start == 0) {
				start = Integer.MIN_VALUE;
			}
		}
		if (end != Integer.MAX_VALUE) {
			sb.append(",\"end\":").append(end);
			if (end == 100) {
				end = Integer.MAX_VALUE;
			}
		}
		if (startValue != null) {
			ComponentPart.encode(sb, "startValue", startValue);
		}
		if (endValue != null) {
			ComponentPart.encode(sb, "endValue", endValue);
		}
		if (minSpan != Integer.MIN_VALUE) {
			sb.append(",\"minSpan\":").append(minSpan);
			if (minSpan == 0) {
				minSpan = Integer.MIN_VALUE;
			}
		}
		if (maxSpan != Integer.MAX_VALUE) {
			sb.append(",\"maxSpan\":").append(maxSpan);
			if (maxSpan == 100) {
				maxSpan = Integer.MAX_VALUE;
			}
		}
		if (minSpanValue != null) {
			ComponentPart.encode(sb, "minValueSpan", minSpanValue);
		}
		if (maxSpanValue != null) {
			ComponentPart.encode(sb, "maxValueSpan", maxSpanValue);
		}
		ComponentPart.encode(sb, "zoomLock", zoomLock);
		ComponentPart.encode(sb, "showDetail", showDetail);
	}

	/**
	 * Show details when dragging.
	 *
	 * @param showDetail
	 *            True/false.
	 */
	public void setShowDetail(final boolean showDetail) {
		this.showDetail = showDetail;
	}

	/**
	 * Get the filter mode. (See {@link #setFilterMode(int)}).
	 *
	 * @return Get the current filter mode.
	 */
	public final int getFilterMode() {
		return filterMode == Integer.MAX_VALUE ? 3 : filterMode;
	}

	/**
	 * <p>
	 * Set the filter mode.
	 * </p>
	 * <p>
	 * 0: Do not filter data.
	 * </p>
	 * <p>
	 * 1: Data that outside the window will be set to NaN, which will not lead to changes of the window of other axes.
	 * </p>
	 * <p>
	 * 2: Data that outside the window will be filtered, which may lead to some changes of windows of other axes. For
	 * each data item, it will be filtered only if all of the relevant dimensions are out of the same side of the
	 * window.
	 * </p>
	 * <p>
	 * 3: Data that outside the window will be filtered, which may lead to some changes of windows of other axes. For
	 * each data item, it will be filtered if one of the relevant dimensions is out of the window. This is the default
	 * value.
	 * </p>
	 * <p>
	 * Note: Setting other values will be ignored.
	 * </p>
	 *
	 * @param filterMode
	 *            Filter mode.
	 */
	public void setFilterMode(final int filterMode) {
		this.filterMode = (filterMode >= 0 && filterMode <= 3) ? filterMode : Integer.MAX_VALUE;
	}

	/**
	 * The start percentage of the window out of the data extent, in the range of 0 ~ 100.
	 *
	 * @return Start percentage.
	 */
	public final int getStart() {
		return start == Integer.MIN_VALUE ? 0 : start;
	}

	/**
	 * The start percentage of the window out of the data extent, in the range of 0 ~ 100. (Value set by
	 * setStartValue(...) methods will not be effective if this method is invoked).
	 *
	 * @param start
	 *            Start percentage.
	 */
	public void setStart(final int start) {
		this.start = (start >= 0 && start < 100) ? start : Integer.MIN_VALUE;
		startValue = null;
	}

	/**
	 * The end percentage of the window out of the data extent, in the range of 0 ~ 100.
	 *
	 * @return End percentage.
	 */
	public final int getEnd() {
		return end;
	}

	/**
	 * The end percentage of the window out of the data extent, in the range of 0 ~ 100.
	 *
	 * @param end
	 *            End percentage.
	 */
	public void setEnd(final int end) {
		this.end = (end > 0 && end <= 100) ? end : Integer.MAX_VALUE;
	}

	/**
	 * The absolute start value of the window.
	 *
	 * @return Start value.
	 */
	public final Object getStartValue() {
		return startValue;
	}

	/**
	 * The absolute start value of the window. (Value set by {@link #setStart(int)} will not be effective if this method
	 * is invoked).
	 *
	 * @param startValue
	 *            Start value. (Can be index value for category data).
	 */
	public void setStartValue(final Number startValue) {
		this.startValue = startValue;
		start = Integer.MIN_VALUE;
	}

	/**
	 * The absolute start value of the window. (Value set by {@link #setStart(int)} will not be effective if this method
	 * is invoked).
	 *
	 * @param startValue
	 *            Start value. (Used for {@link DataType#DATE}).
	 */
	public void setStartValue(final LocalDate startValue) {
		this.startValue = startValue;
		start = Integer.MIN_VALUE;
	}

	/**
	 * The absolute start value of the window. (Value set by {@link #setStart(int)} will not be effective if this method
	 * is invoked).
	 *
	 * @param startValue
	 *            Start value. (Used for {@link DataType#TIME}).
	 */
	public void setStartValue(final LocalDateTime startValue) {
		this.startValue = startValue;
		start = Integer.MIN_VALUE;
	}

	/**
	 * The absolute end value of the window.
	 *
	 * @return End value.
	 */
	public final Object getEndValue() {
		return endValue;
	}

	/**
	 * The absolute end value of the window. (Value set by {@link #setEnd(int)} will not be effective if this method is
	 * invoked).
	 *
	 * @param endValue
	 *            End value. (Can be index value for category data).
	 */
	public void setEndValue(final Number endValue) {
		this.endValue = endValue;
		end = Integer.MAX_VALUE;
	}

	/**
	 * The absolute end value of the window. (Value set by {@link #setEnd(int)} will not be effective if this method is
	 * invoked).
	 *
	 * @param endValue
	 *            End value. (Used for {@link DataType#DATE}).
	 */
	public void setEndValue(final LocalDate endValue) {
		this.endValue = endValue;
		end = Integer.MAX_VALUE;
	}

	/**
	 * The absolute end value of the window. (Value set by {@link #setEnd(int)} will not be effective if this method is
	 * invoked).
	 *
	 * @param endValue
	 *            End value. (Used for {@link DataType#TIME}).
	 */
	public void setEndValue(final LocalDateTime endValue) {
		this.endValue = endValue;
		end = Integer.MAX_VALUE;
	}

	/**
	 * The minimum span percentage value of the window out of the data extent, in the range of 0 ~ 100.
	 *
	 * @return Minimum span percentage value.
	 */
	public final int getMinSpan() {
		return minSpan == Integer.MIN_VALUE ? 0 : minSpan;
	}

	/**
	 * The minimum span percentage value of the window out of the data extent, in the range of 0 ~ 100. (Value set by
	 * setMinSpanValue(...) methods will not be effective if this method is invoked).
	 *
	 * @param minSpan
	 *            Minimum span percentage value.
	 */
	public void setMinSpan(final int minSpan) {
		this.minSpan = (minSpan >= 0 && minSpan < 100) ? minSpan : Integer.MIN_VALUE;
		minSpanValue = null;
	}

	/**
	 * The maximum span percentage value of the window out of the data extent, in the range of 0 ~ 100.
	 *
	 * @return MaxSpan Maximum span percentage value.
	 */
	public final int getMaxSpan() {
		return maxSpan;
	}

	/**
	 * The maximum span percentage value of the window out of the data extent, in the range of 0 ~ 100.
	 *
	 * @param maxSpan
	 *            Maximum span percentage value.
	 */
	public void setMaxSpan(final int maxSpan) {
		this.maxSpan = (maxSpan > 0 && maxSpan <= 100) ? maxSpan : Integer.MAX_VALUE;
	}

	/**
	 * The absolute minimum span value of the window.
	 *
	 * @return Minimum span value.
	 */
	public final Object getMinSpanValue() {
		return minSpanValue;
	}

	/**
	 * The absolute minimum span value of the window. (Value set by {@link #setMinSpan(int)} will not be effective if
	 * this method is invoked).
	 *
	 * @param minSpanValue
	 *            Minimum span value.
	 */
	public void setMinSpanValue(final Number minSpanValue) {
		this.minSpanValue = minSpanValue;
		minSpan = Integer.MIN_VALUE;
	}

	/**
	 * The absolute minimum span value of the window. (Value set by {@link #setMinSpan(int)} will not be effective if
	 * this method is invoked).
	 *
	 * @param minSpanValue
	 *            Minimum span value. (Used for {@link DataType#DATE}).
	 */
	public void setMinSpanValue(final LocalDate minSpanValue) {
		this.minSpanValue = minSpanValue;
		minSpan = Integer.MIN_VALUE;
	}

	/**
	 * The absolute minimum span value of the window. (Value set by {@link #setMinSpan(int)} will not be effective if
	 * this method is invoked).
	 *
	 * @param minSpanValue
	 *            Minimum span value. (Used for {@link DataType#TIME}).
	 */
	public void setMinSpanValue(final LocalDateTime minSpanValue) {
		this.minSpanValue = minSpanValue;
		minSpan = Integer.MIN_VALUE;
	}

	/**
	 * The absolute maximum span value of the window.
	 *
	 * @return Maximum span value.
	 */
	public final Object getMaxSpanValue() {
		return maxSpanValue;
	}

	/**
	 * The absolute maximum span value of the window. (Value set by {@link #setMaxSpan(int)} will not be effective if
	 * this method is invoked).
	 *
	 * @param maxSpanValue
	 *            Maximum span value.
	 */
	public void setMaxSpanValue(final Number maxSpanValue) {
		this.maxSpanValue = maxSpanValue;
		maxSpan = Integer.MAX_VALUE;
	}

	/**
	 * The absolute maximum span value of the window. (Value set by {@link #setMaxSpan(int)} will not be effective if
	 * this method is invoked).
	 *
	 * @param maxSpanValue
	 *            Maximum span value. (Used for {@link DataType#DATE}).
	 */
	public void setMaxSpanValue(final LocalDate maxSpanValue) {
		this.maxSpanValue = maxSpanValue;
		maxSpan = Integer.MAX_VALUE;
	}

	/**
	 * The absolute maximum span value of the window. (Value set by {@link #setMaxSpan(int)} will not be effective if
	 * this method is invoked).
	 *
	 * @param maxSpanValue
	 *            Maximum span value. (Used for {@link DataType#TIME}).
	 */
	public void setMaxSpanValue(final LocalDateTime maxSpanValue) {
		this.maxSpanValue = maxSpanValue;
		maxSpan = Integer.MAX_VALUE;
	}

	/**
	 * Check whether zoom lock is set or not. (See {@link #setZoomLock(boolean)} for details).
	 *
	 * @return True or false.
	 */
	public final boolean isZoomLock() {
		return zoomLock;
	}

	/**
	 * <p>
	 * Set the zoom lock.
	 * </p>
	 * <p>
	 * When set as true, the size of window is locked, that is, only the translation (by mouse drag or touch drag) is
	 * available.
	 * </p>
	 *
	 * @param zoomLock
	 *            True or false.
	 */
	public void setZoomLock(final boolean zoomLock) {
		this.zoomLock = zoomLock;
	}
}
