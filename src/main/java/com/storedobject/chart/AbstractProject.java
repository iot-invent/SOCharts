/*
 *  Copyright 2019-2021 Syam Pillai
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.storedobject.helper.ID;

/**
 * An abstract base class that implements some useful attributes and methods for defining a project.
 *
 * @author Syam
 */
public abstract class AbstractProject {

	private final long id = ID.newID();
	private String name;
	private final ChronoUnit durationType;
	private LocalDateTime start = null;
	private Function<LocalDateTime, String> todayFormat, tooltipTimeFormat;
	private LocalDateTime today;
	private Color todayColor;
	private String bandColorEven, bandColorOdd;
	private int taskFontSize = 12, groupFontSize = 12, extraFontSize = 9;

	/**
	 * Constructor.
	 *
	 * @param durationType
	 *            Type of duration to be used for this project. (Note: {@link ChronoUnit#ERAS} and
	 *            {@link ChronoUnit#FOREVER} are not supported and if used, will be considered as
	 *            {@link ChronoUnit#MILLIS}).
	 */
	public AbstractProject(ChronoUnit durationType) {
		if (durationType == null) {
			durationType = ChronoUnit.DAYS;
		} else {
			switch (durationType) {
			case DAYS:
			case HOURS:
			case MINUTES:
			case SECONDS:
				// durationType=durationType;
				break;
			default:
				durationType = ChronoUnit.MILLIS;
				break;
			}
			;
		}
		this.durationType = durationType;
		if (durationType.isDateBased()) {
			today = LocalDate.now().atStartOfDay();
		} else {
			today = LocalDateTime.now();
		}
	}

	/**
	 * Get the duration type of this project.
	 *
	 * @return Duration type.
	 */
	public final ChronoUnit getDurationType() {
		return durationType;
	}

	/**
	 * Set the name of the project.
	 *
	 * @param name
	 *            Name of the project.
	 */
	public void setName(final String name) {
		this.name = name;
	}

	/**
	 * Get the name of the project.
	 *
	 * @return Project name.
	 */
	public final String getName() {
		return name == null || name.isEmpty() ? ("Project " + id) : name;
	}

	/**
	 * Is this project empty?
	 *
	 * @return True if no data exist.
	 */
	boolean isEmpty() {
		return getRowCount() == 0;
	}

	/**
	 * Get the row count (How many rows are required for rendering?).
	 *
	 * @return row count.
	 */
	abstract int getRowCount();

	/**
	 * Trim down a {@link LocalDateTime} to a {@link LocalDate}.
	 *
	 * @param dateTime
	 *            Time to trim.
	 * @return Local date.
	 */
	protected LocalDateTime trim(final LocalDateTime dateTime) {
		return dateTime.truncatedTo(durationType);
	}

	/**
	 * Set the start of the project. Depending on the duration type, one of the "setStart" methods can be used. However,
	 * the start value will be appropriately trimmed if you try to set a higher resolution value.
	 *
	 * @param start
	 *            Start of the project.
	 */
	public void setStart(final LocalDateTime start) {
		this.start = trim(start);
	}

	/**
	 * Set the start of the project. Depending on the duration type, one of the "setStart" methods can be used. However,
	 * the start value will be appropriately trimmed if you try to set a higher resolution value.
	 *
	 * @param start
	 *            Start of the project.
	 */
	public final void setStart(final LocalDate start) {
		setStart(start.atStartOfDay());
	}

	/**
	 * Set the start of the project. Depending on the duration type, one of the "setStart" methods can be used. However,
	 * the start value will be appropriately trimmed if you try to set a higher resolution value.
	 *
	 * @param start
	 *            Start of the project.
	 */
	public final void setStart(final Instant start) {
		setStart(LocalDateTime.from(start));
	}

	/**
	 * Get the project start.
	 *
	 * @return Start date/time.
	 */
	public LocalDateTime getStart() {
		return start;
	}

	/**
	 * Get the project end.
	 *
	 * @return End date/time.
	 */
	public abstract LocalDateTime getEnd();

	/**
	 * Validate constraints if any and throw a {@link ChartException} if not valid.
	 */
	void validateConstraints() throws ChartException {
		if (getStart() == null) {
			throw new ChartException("Project start not specified");
		}
	}

	/**
	 * Get max of 2 time values.
	 *
	 * @param one
	 *            First value.
	 * @param two
	 *            Second value.
	 * @return Max of the 2.
	 */
	protected static LocalDateTime max(final LocalDateTime one, final LocalDateTime two) {
		if (one == null && two == null) {
			return null;
		}
		if (one == null) {
			return two;
		}
		if (two == null) {
			return one;
		}
		return one.isBefore(two) ? two : one;
	}

	/**
	 * Encode time data according to the duration type.
	 *
	 * @param time
	 *            Time.
	 * @return Formatted value.
	 */
	protected final String encode(final LocalDateTime time) {
		return "\"" + (durationType.isDateBased() ? time.toLocalDate() : time) + "\"";
	}

	private <T> Stream<T> taskData(final BiFunction<AbstractTask, Integer, T> encoder,
			final Predicate<AbstractTask> taskFilter) {
		return StreamSupport.stream(new TaskIterable<>(encoder, taskFilter).spliterator(), false);
	}

	private class TaskIterable<T> implements Iterable<T> {

		private final BiFunction<AbstractTask, Integer, T> encoder;
		private final Predicate<AbstractTask> taskFilter;

		private TaskIterable(final BiFunction<AbstractTask, Integer, T> encoder,
				final Predicate<AbstractTask> taskFilter) {
			this.encoder = encoder;
			this.taskFilter = taskFilter;
		}

		@Override
		public Iterator<T> iterator() {
			return AbstractProject.this.iterator(encoder, taskFilter);
		}
	}

	/**
	 * Provide an iterator of tasks/activities.
	 *
	 * @param encoder
	 *            Encoder used to encode the task/activity to the desired output type. (A function that takes the
	 *            task/activity and the index as the input).
	 * @param taskFilter
	 *            Filter to apply while selecting the task/activity.
	 * @param <T>
	 *            Type of output required.
	 * @return Iterator that returns encoded values of the task/activity.
	 */
	abstract <T> Iterator<T> iterator(BiFunction<AbstractTask, Integer, T> encoder, Predicate<AbstractTask> taskFilter);

	/**
	 * Data for rendering the task/activity bands.
	 *
	 * @return Band data.
	 */
	AbstractDataProvider<String> bands() {
		if (bandColorOdd == null) {
			bandColorOdd = "#D9E1F2";
		}
		if (bandColorEven == null) {
			bandColorEven = "#EEF0F3";
		}
		if (!bandColorOdd.startsWith("\"")) {
			bandColorOdd = "\"" + bandColorOdd + "\"";
		}
		if (!bandColorEven.startsWith("\"")) {
			bandColorEven = "\"" + bandColorEven + "\"";
		}
		final LocalDateTime end = getEnd();
		return dataProvider(DataType.OBJECT, (t, i) -> "[" + i + "," + AbstractProject.this.encode(start) + ","
				+ AbstractProject.this.encode(end) + "," + (i % 2 == 0 ? bandColorEven : bandColorOdd) + "]");
	}

	/**
	 * Trim a double value to remove unnecessary decimals.
	 *
	 * @param v
	 *            Value to trim.
	 * @return Trimmed value as a string.
	 */
	protected static String trim(final double v) {
		String t = String.valueOf(v);
		if (t.endsWith(".0")) {
			t = t.substring(0, t.indexOf('.'));
		}
		return t;
	}

	/**
	 * Get the label of the given task/activity (Will be used for displaying the label on the task/activity bar). The
	 * default implementation returns {@link AbstractTask#getName()} + " (" + {@link AbstractTask#getCompleted()} +
	 * "%)".
	 * <p>
	 * In the case of a milestone ({@link AbstractTask#isMilestone()}), it returns just the milestone name.
	 * </p>
	 *
	 * @param task
	 *            Task/activity.
	 * @return Label.
	 */
	protected String getLabel(final AbstractTask task) {
		if (task.isMilestone()) {
			return task.getName();
		}
		return task.getName() + " (" + trim(task.getCompleted()) + "%)";
	}

	/**
	 * Get data for rendering tasks/activities. (For internal use only).
	 *
	 * @return Task/activity data.
	 */
	AbstractDataProvider<String> taskData() {
		final BiFunction<AbstractTask, Integer, String> func = (t, i) -> "[" + renderingPosition(t, i) + ",\""
				+ getLabel(t) + "\"," + encode(t.renderStart()) + "," + encode(t.getEnd()) + ","
				+ (t.isMilestone() ? 100 : t.getCompleted()) + "," + t.getColor() + ",\"black\",\"black\","
				+ taskFontSize(t) + "]";
		return dataProvider(DataType.OBJECT, func);
	}

	/**
	 * Determine the font-size for the group.
	 *
	 * @param t
	 *            Task/activity
	 * @return Font-size
	 */
	int groupFontSize(final AbstractTask t) {
		final int f = t.getFontSize();
		return f <= 0 ? getGroupFontSize() : f;
	}

	/**
	 * Determine the font-size for the task/activity details.
	 *
	 * @param t
	 *            Task/activity
	 * @return Font-size
	 */
	int taskFontSize(final AbstractTask t) {
		final int f = t.getFontSize();
		return f <= 0 ? getTaskFontSize() : f;
	}

	/**
	 * Determine the font-size for the extra details of the task/activity.
	 *
	 * @param t
	 *            Task/activity
	 * @return Font-size
	 */
	int extraFontSize(final AbstractTask t) {
		final int f = t.getExtraFontSize();
		return f <= 0 ? getExtraFontSize() : f;
	}

	/**
	 * The rendering position of the task/activity on the Y-axis.
	 *
	 * @param task
	 *            Task/activity.
	 * @param index
	 *            Index of the task/activity in the full list of tasks/activities.
	 * @return Position on the Y-axis.
	 */
	int renderingPosition(final AbstractTask task, final int index) {
		return index;
	}

	/**
	 * Get data for rendering task dependencies. (Not applicable to activities).
	 *
	 * @return Task dependencies.
	 */
	AbstractDataProvider<String> dependencies() {
		return null;
	}

	/**
	 * Get today/now.
	 *
	 * @return Today/now.
	 */
	public final LocalDateTime getToday() {
		return today;
	}

	/**
	 * Get the extra axis label for the task/activity. (Will be used for displaying the 2nd line of the axis label).
	 *
	 * @param task
	 *            Task.
	 * @return Label.
	 */
	protected abstract String getExtraAxisLabel(AbstractTask task);

	/**
	 * Get the axis label for the task/activity task or group. (Will be used for displaying the axis label).
	 * <p>
	 * The default implementation returns the name of the task/activity.
	 * </p>
	 *
	 * @param task
	 *            Task/activity.
	 * @return Label.
	 */
	protected String getAxisLabel(final AbstractTask task) {
		return task.getName();
	}

	/**
	 * Get the task axis label for the given task/activity. (Will be used for displaying the axis label). For internal
	 * use only.
	 *
	 * @param task
	 *            Task/activity data.
	 * @param index
	 *            Index of the task/activity data.
	 * @return Label to be used for rendering the task/activity axis labels.
	 */
	abstract String getAxisLabel(AbstractTask task, int index);

	/**
	 * Get labels for the Y-axis (For internal use only).
	 *
	 * @return Labels for Y-axis.
	 */
	abstract AbstractDataProvider<String> axisLabels();

	/**
	 * Get the tooltip label of the given task/activity (Will be used for displaying the tooltip label).
	 *
	 * @param task
	 *            Task/activity.
	 * @return Label.
	 */
	protected abstract String getTooltipLabel(AbstractTask task);

	/**
	 * Get labels for tooltip for the tasks/activities.
	 *
	 * @return Tooltip labels for tasks/activities.
	 */
	final AbstractDataProvider<String> tooltipLabels() {
		return dataProvider(DataType.CATEGORY, (t, i) -> "\"" + getTooltipLabel(t) + "\"");
	}

	/**
	 * Create a data provider of some type from the stream of {@link AbstractTask}.
	 *
	 * @param dataType
	 *            Data type.
	 * @param encoder
	 *            Encoder for encoding the task/activity data.
	 * @param <T>
	 *            Type of data provider.
	 * @return An instance of a {@link BasicDataProvider}.
	 */
	<T> AbstractDataProvider<T> dataProvider(final DataType dataType,
			final BiFunction<AbstractTask, Integer, T> encoder) {
		return dataProvider(dataType, encoder, null);
	}

	/**
	 * Create a data provider of some type from the stream of {@link AbstractTask}.
	 *
	 * @param dataType
	 *            Data type.
	 * @param encoder
	 *            Encoder for encoding the task/activity data.
	 * @param taskFilter
	 *            Filter to be applied while selecting the tasks/activities.
	 * @param <T>
	 *            Type of data provider.
	 * @return An instance of a {@link BasicDataProvider}.
	 */
	<T> AbstractDataProvider<T> dataProvider(final DataType dataType,
			final BiFunction<AbstractTask, Integer, T> encoder, final Predicate<AbstractTask> taskFilter) {
		return new BasicDataProvider<>() {

			@Override
			public Stream<T> stream() {
				return taskData(encoder, taskFilter);
			}

			@Override
			public DataType getDataType() {
				return dataType;
			}

			@Override
			public void encode(final StringBuilder sb, final T value) {
				sb.append(value);
			}
		};
	}

	/**
	 * Data required for rendering today/time marker.
	 *
	 * @return Data for today/time marker.
	 */
	AbstractDataProvider<?> dataForToday() {
		return new BasicDataProvider<>() {

			@Override
			public Stream<Object> stream() {
				return Stream.of("");
			}

			@Override
			public void encode(final StringBuilder sb, final Object value) {
				if (todayColor == null) {
					todayColor = new Color("#FF000");
				}
				sb.append("[").append(AbstractProject.this.encode(today)).append(",\"")
						.append(getTodayFormat().apply(today)).append("\",").append(todayColor).append(",100]");
			}
		};
	}

	/**
	 * Set today. By default, today = {@link LocalDate#now()}. However, it is possible to set another date. (Used in
	 * date-based projects).
	 *
	 * @param today
	 *            Value of today.
	 */
	public void setToday(final LocalDate today) {
		this.today = today.atStartOfDay();
	}

	/**
	 * Set the color for today-marker on the Gantt chart. (Used in date-based projects).
	 *
	 * @param color
	 *            Color.
	 */
	public void setTodayColor(final Color color) {
		todayColor = color;
	}

	/**
	 * Set time-now. By default, now = {@link LocalDateTime#now()}. However, it is possible to set another date or time.
	 * (Used in time-based projects).
	 *
	 * @param now
	 *            Value of time-now.
	 */
	public void setTimeNow(final LocalDateTime now) {
		today = now;
	}

	/**
	 * Set the color for time-now-marker on the Gantt chart. (Used in time-based projects).
	 *
	 * @param color
	 *            Color.
	 */
	public void setTimeNowColor(final Color color) {
		todayColor = color;
	}

	/**
	 * Set a formatter function to format the today-marker. By default, today is formatted as follows:
	 * <p>
	 * Today: Jan 23, 1998
	 * </p>
	 *
	 * @param todayFormat
	 *            Formatter function.
	 */
	public void setTodayFormat(final Function<LocalDateTime, String> todayFormat) {
		this.todayFormat = todayFormat;
	}

	/**
	 * Set a formatter function to format the tooltip of tasks/activities. By default, it is formatted as follows:
	 * <p>
	 * Aug 07, 2002
	 * </p>
	 *
	 * @param tooltipTimeFormat
	 *            Formatter function.
	 */
	public void setTooltipTimeFormat(final Function<LocalDateTime, String> tooltipTimeFormat) {
		this.tooltipTimeFormat = tooltipTimeFormat;
	}

	private String timePattern() {
		final StringBuilder s = new StringBuilder("MMM dd, yyyy");
		if (!durationType.isDateBased()) {
			s.append(' ');
			switch (durationType) {
			case SECONDS:
				s.append("HH:mm:ss");
				break;
			case MINUTES:
				s.append("HH:mm");
				break;
			case HOURS:
				s.append("HH");
				break;
			default:
				s.append("HH:mm:ss.S");
				break;
			}
		}
		return s.toString();
	}

	/**
	 * Get the format for the date tooltip.
	 *
	 * @return Format function.
	 */
	Function<LocalDateTime, String> getTodayFormat() {
		if (todayFormat == null) {
			final String s = timePattern();
			final String name = durationType.isDateBased() ? "Date" : "Time";
			todayFormat = d -> name + ": " + DateTimeFormatter.ofPattern(s).format(d);
		}
		return todayFormat;
	}

	/**
	 * Get the format for the time tooltip.
	 *
	 * @return Format function.
	 */
	Function<LocalDateTime, String> getTooltipTimeFormat() {
		if (tooltipTimeFormat == null) {
			final String s = timePattern();
			tooltipTimeFormat = d -> DateTimeFormatter.ofPattern(s).format(d);
		}
		return tooltipTimeFormat;
	}

	/**
	 * Set the colors of the task/activity bands.
	 *
	 * @param bandColorOdd
	 *            Color for odd rows.
	 * @param bandColorEven
	 *            Color for even rows.
	 */
	public void setTaskBandColors(final Color bandColorOdd, final Color bandColorEven) {
		this.bandColorOdd = bandColorOdd.toString();
		this.bandColorEven = bandColorEven.toString();
	}

	/**
	 * Get the font-size used to render the extra information of group.
	 *
	 * @return Font-size. Default is 12.
	 */
	public int getExtraFontSize() {
		return extraFontSize;
	}

	/**
	 * Set the font size used to render the extra information of group.
	 * <p>
	 * Note: You can also set different values for individual groups. See {@link AbstractTask#setFontSize(int)}
	 * </p>
	 *
	 * @param extraFontSize
	 *            Font-size to set (in pixels). Default is 9.
	 */
	public void setExtraFontSize(final int extraFontSize) {
		this.extraFontSize = extraFontSize;
	}

	/**
	 * Get the font-size used to render the task/activity group.
	 *
	 * @return Font-size. Default is 12.
	 */
	public int getGroupFontSize() {
		return groupFontSize;
	}

	/**
	 * Set the font size used to render the group name.
	 * <p>
	 * Note: You can also set different values for individual groups. See {@link AbstractTask#setFontSize(int)}
	 * </p>
	 *
	 * @param groupFontSize
	 *            Font-size to set (in pixels). Default is 12.
	 */
	public void setGroupFontSize(final int groupFontSize) {
		this.groupFontSize = groupFontSize <= 0 ? 12 : groupFontSize;
	}

	/**
	 * Get the font-size used to render the task details
	 *
	 * @return Font-size. Default is 12.
	 */
	public int getTaskFontSize() {
		return taskFontSize;
	}

	/**
	 * Set the font size used to render the task details.
	 * <p>
	 * Note: You can also set different values for individual tasks. See {@link AbstractTask#setFontSize(int)}
	 * </p>
	 *
	 * @param taskFontSize
	 *            Font-size to set (in pixels). Default is 12.
	 */
	public void setTaskFontSize(final int taskFontSize) {
		this.taskFontSize = taskFontSize <= 0 ? 12 : taskFontSize;
	}

	/**
	 * Get the font-size used to render the activity details.
	 *
	 * @return Font-size. Default is 12.
	 */
	public int getActivityFontSize() {
		return taskFontSize;
	}

	/**
	 * Set the font size used to render the activity details.
	 * <p>
	 * Note: You can also set different values for individual activities. See {@link AbstractTask#setFontSize(int)}
	 * </p>
	 *
	 * @param activityFontSize
	 *            Font-size to set (in pixels). Default is 12.
	 */
	public void setActivityFontSize(final int activityFontSize) {
		setTaskFontSize(activityFontSize);
	}
}
