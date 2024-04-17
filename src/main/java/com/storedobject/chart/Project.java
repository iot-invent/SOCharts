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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Class to represent a project.
 * <p>
 * {@link GanttChart} uses data from this class.
 * </p>
 * <p>
 * A project consists of instances of {@link Task}s (can be created via {@link #createTask(String, int)}). You can also
 * have {@link TaskGroup}s and specify that a {@link Task} instance belongs to a specific {@link TaskGroup} instance
 * while creating it via {@link #createTask(TaskGroup, String, int)} and {@link TaskGroup}s can be created via
 * {@link #createTaskGroup(String)}. Alternatively, a {@link Task} can be created under a {@link TaskGroup} via
 * {@link TaskGroup#createTask(String, int)}.
 * </p>
 * <p>
 * Dependencies between {@link Task}s and/or {@link TaskGroup}s can be specified via
 * {@link #dependsOn(ProjectTask, ProjectTask)}.
 * </p>
 * <p>
 * Note: Even though this class is designed to provide data for the {@link GanttChart}, it can be used independently for
 * scheduling tasks / task groups involved in a typical project. Once you defined the project by adding all the task
 * groups, tasks and dependencies, you can just use methods like {@link #streamGroups()},
 * {@link #streamTasks(TaskGroup)} and {@link #streamDependencies(ProjectTask)} to retrieve the data related to the
 * project. All those methods return data after auto-scheduling the tasks in the project.
 * </p>
 *
 * @author Syam
 */
public class Project extends AbstractProject {

	private final List<TaskGroup> taskGroups = new ArrayList<>();
	private boolean checked = false;

	/**
	 * Constructor for a date-based project.
	 */
	public Project() {
		this(null);
	}

	/**
	 * Constructor.
	 *
	 * @param durationType
	 *            Type of duration to be used for this project. (Note: {@link ChronoUnit#ERAS} and
	 *            {@link ChronoUnit#FOREVER} are not supported and if used, will be considered as
	 *            {@link ChronoUnit#MILLIS}).
	 */
	public Project(final ChronoUnit durationType) {
		super(durationType);
	}

	/**
	 * Validate dependencies. A {@link ChartException} is thrown if any circular dependency is found.
	 * <p>
	 * Important: This method removes any "task group" from this project that does not have any "task".
	 * </p>
	 */
	@Override
	void validateConstraints() throws ChartException {
		if (checked) {
			return;
		}
		super.validateConstraints();
		taskGroups.removeIf(tg -> tg.tasks.isEmpty());
		for (final TaskGroup taskGroup : taskGroups) {
			validateDependency(taskGroup);
			for (final Task task : taskGroup.tasks) {
				validateDependency(task);
			}
		}
		schedule();
		taskGroups.sort(Project::compare);
		taskGroups.forEach(TaskGroup::sort);
		checked = true;
	}

	@Override
	public boolean isEmpty() {
		return taskGroups.isEmpty();
	}

	private void schedule() {
		final LocalDateTime start = getStart();
		for (final TaskGroup taskGroup : taskGroups) {
			taskGroup.start = start;
			taskGroup.tasks.forEach(t -> t.start = start);
		}
		adjustTaskStart();
		adjustGroupStart();
		adjustStart();
		while (adjustTaskStart()) {
			if (!adjustGroupStart() && !adjustStart()) {
				break;
			}
		}
	}

	private boolean adjustTaskStart() {
		boolean adjusted = false;
		for (final TaskGroup taskGroup : taskGroups) {
			for (final Task task : taskGroup.tasks) {
				if (task.applyTaskStartDependency()) {
					adjusted = true;
				}
			}
		}
		return adjusted;
	}

	private boolean adjustGroupStart() {
		boolean adjusted = false;
		for (final TaskGroup taskGroup : taskGroups) {
			if (taskGroup.applyGroupStartDependency()) {
				adjusted = true;
			}
		}
		return adjusted;
	}

	private boolean adjustStart() {
		boolean adjusted = false;
		for (final TaskGroup taskGroup : taskGroups) {
			if (taskGroup.applyStartDependency()) {
				adjusted = true;
			}
			for (final Task task : taskGroup.tasks) {
				if (task.applyStartDependency()) {
					adjusted = true;
				}
			}
		}
		return adjusted;
	}

	private static void validateDependency(final ProjectTask instance) throws ChartException {
		if (validateDependency(instance, instance.predecessors)) {
			throw new ChartException("Circular dependency: Task " + (instance instanceof TaskGroup ? "Group " : "")
					+ '\'' + instance.getName() + '\'');
		}
	}

	private static boolean validateDependency(final ProjectTask instance, final List<ProjectTask> predecessors) {
		if (predecessors.contains(instance)) {
			return true;
		}
		for (final ProjectTask a : predecessors) {
			if (a instanceof TaskGroup && instance instanceof Task && ((TaskGroup) a).tasks.contains(instance)) {
				return true;
			}
			if (a instanceof Task && instance instanceof TaskGroup && ((TaskGroup) instance).equals(((Task) a).group)) {
				return true;
			}
			if (validateDependency(instance, a.predecessors)) {
				return true;
			}
		}
		return false;
	}

	private boolean contains(ProjectTask instance) {
		if (instance instanceof Task) {
			final Task task = (Task) instance;
			if (task.group == null) {
				return false;
			}
			instance = task.group;
		}
		return taskGroups.contains(instance);
	}

	/**
	 * Create and add a {@link TaskGroup} to the project.
	 *
	 * @param name
	 *            Name of the task group.
	 * @return Task group created.
	 */
	public TaskGroup createTaskGroup(final String name) {
		final TaskGroup taskGroup = new TaskGroup(name);
		taskGroups.add(0, taskGroup);
		return taskGroup;
	}

	/**
	 * Create and add a {@link Task} to the project. (This method should be used only if there is no need of "grouping
	 * of tasks".)
	 *
	 * @param name
	 *            Name of the task.
	 * @param duration
	 *            Duration of the task. (A duration of zero denotes a project milestone).
	 * @return Task created.
	 */
	public Task createTask(final String name, final int duration) {
		return createTask(createTaskGroup("DEFAULT"), name, duration);
	}

	/**
	 * Create and add a {@link Task} to the project.
	 *
	 * @param taskGroup
	 *            Group to which the task to be added.
	 * @param taskName
	 *            Name of the task.
	 * @param duration
	 *            Duration of the task. (A duration of zero denotes a project milestone).
	 * @return Task created. (Returns <code>null</code> if the {@link TaskGroup} passed doesn't belong to this project).
	 */
	public Task createTask(final TaskGroup taskGroup, final String taskName, final int duration) {
		if (taskGroups.contains(taskGroup)) {
			return new Task(taskGroup, taskName, duration);
		}
		return null;
	}

	private void deleteTask(final Task task) {
		if (task == null) {
			return;
		}
		checked = false;
		task.group.tasks.remove(task);
		taskGroups.forEach(taskGroup -> taskGroup.tasks.forEach(t -> t.predecessors.remove(task)));
	}

	private void deleteGroup(final TaskGroup taskGroup) {
		if (taskGroup == null) {
			return;
		}
		checked = false;
		taskGroups.remove(taskGroup);
		while (!taskGroup.tasks.isEmpty()) {
			deleteTask(taskGroup.tasks.get(0));
		}
	}

	/**
	 * Delete {@link TaskGroup}s from the project. (All tasks and dependencies related to these groups will be dropped).
	 *
	 * @param taskGroups
	 *            {@link TaskGroup}s to delete.
	 */
	public void delete(final TaskGroup... taskGroups) {
		if (taskGroups != null) {
			for (final TaskGroup taskGroup : taskGroups) {
				deleteGroup(taskGroup);
			}
		}
	}

	/**
	 * Delete {@link Task}s from the project. (All dependencies related to these tasks will be dropped).
	 *
	 * @param tasks
	 *            {@link Task}s to delete.
	 */
	public void delete(final Task... tasks) {
		if (tasks != null) {
			for (final Task task : tasks) {
				deleteTask(task);
			}
		}
	}

	/**
	 * Specify that a task/group depends on another task/group.
	 *
	 * @param dependent
	 *            Dependent.
	 * @param predecessor
	 *            Predecessor.
	 */
	public void dependsOn(final ProjectTask dependent, final ProjectTask predecessor) {
		if (dependent == null || predecessor == null) {
			return;
		}
		final List<ProjectTask> predecessors = dependent.predecessors;
		if (!predecessors.contains(predecessor)) {
			predecessors.add(predecessor);
		}
	}

	@Override
	public final void setStart(final LocalDateTime start) {
		checked = false;
		super.setStart(start);
	}

	@Override
	public final LocalDateTime getEnd() {
		final LocalDateTime start = getStart();
		LocalDateTime end = start, e;
		for (final TaskGroup taskGroup : taskGroups) {
			for (final Task task : taskGroup.tasks) {
				e = task.getEnd();
				if (e.isAfter(end)) {
					end = e;
				}
			}
		}
		return end;
	}

	/**
	 * Reset the earliest start set via one of the "setEarliestStart" methods so that the constraint is no more
	 * applicable.
	 *
	 * @param task
	 *            Task for which "earliest start" needs to be reset.
	 */
	public void resetEarliestStart(final ProjectTask task) {
		checked = false;
		task.earliestStart = null;
	}

	/**
	 * Set the "earliest start" possible for a task/group. Depending on the duration type, one of the "setEarliestStart"
	 * methods can be used. However, the value will be appropriately trimmed if you try to set a higher resolution
	 * value. This is applied as a constraint when tasks are scheduled.
	 * <p>
	 * Note: You can use {@link #resetEarliestStart(ProjectTask)} to undo the effect of this constraint.
	 * </p>
	 *
	 * @param task
	 *            Task/group for which the "earliest start" should be set.
	 * @param start
	 *            Earliest start possible for the task/group.
	 */
	public void setEarliestStart(final ProjectTask task, final LocalDateTime start) {
		checked = false;
		task.earliestStart = trim(start);
	}

	/**
	 * Set the "earliest start" possible for a task/group. Depending on the duration type, one of the "setEarliestStart"
	 * methods can be used. However, the value will be appropriately trimmed if you try to set a higher resolution
	 * value. This is applied as a constraint when tasks are scheduled.
	 * <p>
	 * Note: You can use {@link #resetEarliestStart(ProjectTask)} to undo the effect of this constraint.
	 * </p>
	 *
	 * @param task
	 *            Task/group for which the "earliest start" should be set.
	 * @param start
	 *            Earliest start possible for the task/group.
	 */
	public void setEarliestStart(final ProjectTask task, final LocalDate start) {
		setEarliestStart(task, start.atStartOfDay());
	}

	/**
	 * Set the "earliest start" possible for a task/group. Depending on the duration type, one of the "setEarliestStart"
	 * methods can be used. However, the value will be appropriately trimmed if you try to set a higher resolution
	 * value. This is applied as a constraint when tasks are scheduled.
	 * <p>
	 * Note: You can use {@link #resetEarliestStart(ProjectTask)} to undo the effect of this constraint.
	 * </p>
	 *
	 * @param task
	 *            Task/group for which the "earliest start" should be set.
	 * @param start
	 *            Earliest start possible for the task/group.
	 */
	public void setEarliestStart(final ProjectTask task, final Instant start) {
		setEarliestStart(task, LocalDateTime.from(start));
	}

	private static int compare(final ProjectTask a1, final ProjectTask a2) {
		final int c = a2.getStart().compareTo(a1.getStart());
		return c == 0 ? Integer.compare(a1.order, a2.order) : c;
	}

	@Override
	public final int getRowCount() {
		return taskGroups.stream().mapToInt(tg -> tg.tasks.size()).sum();
	}

	/**
	 * Get the {@link TaskGroup} of this project.
	 *
	 * @param groupIndex
	 *            Index of the task group to be retrieved.
	 * @return {@link TaskGroup}.
	 */
	public final TaskGroup getTaskGroup(final int groupIndex) {
		return taskGroups.get(groupIndex);
	}

	/**
	 * Get the number of {@link Task} belonging to this group.
	 *
	 * @return Task count.
	 */
	public final int getGroupCount() {
		return taskGroups.size();
	}

	/**
	 * An abstract base class for the representation of a Task or Task Group.
	 *
	 * @author Syam
	 */
	abstract class ProjectTask extends AbstractTask {

		private int order = -1;
		/**
		 * Possible earliest start of the task/group.
		 */
		LocalDateTime earliestStart = null;
		/**
		 * List of predecessors.
		 */
		final List<ProjectTask> predecessors = new ArrayList<>();

		/**
		 * Constructor.
		 *
		 * @param name
		 *            Name of the task/group.
		 */
		protected ProjectTask(final String name) {
			setName(name);
		}

		/**
		 * Get the name.
		 *
		 * @return Current name.
		 */
		@Override
		public final String getName() {
			final String name = super.getName();
			if (name != null && !name.isEmpty()) {
				return "DEFAULT".equals(name) ? "" : name;
			}
			return (this instanceof Task ? "Task" : "Group") + ": " + getId();
		}

		/**
		 * Set the order. Start time and dependencies decide the order of a task/group. This parameter is used to decide
		 * on the ordering only when there is a tie. Mostly, it is not required to set this value.
		 *
		 * @param order
		 *            The order to set.
		 */
		public void setOrder(final int order) {
			this.order = order;
		}

		/**
		 * Get the order.
		 *
		 * @return Current order.
		 */
		public int getOrder() {
			return order;
		}

		/**
		 * Get the duration.
		 *
		 * @return Duration (in {@link #getDurationType()}).
		 */
		public abstract int getDuration();

		/**
		 * Get the start.
		 *
		 * @return Start.
		 */
		@Override
		public LocalDateTime getStart() {
			start = max(earliestStart, start);
			return start;
		}

		/**
		 * Get the end.
		 *
		 * @return End.
		 */
		@Override
		public final LocalDateTime getEnd() {
			return getStart().plus(getDuration(), getDurationType());
		}

		/**
		 * Adjust start based on the dependencies. (For internal use only)
		 *
		 * @return True if any adjustment done.
		 */
		boolean applyTaskStartDependency() {
			return applyStartDependency(a -> a instanceof TaskGroup);
		}

		/**
		 * Adjust start based on the dependencies. (For internal use only)
		 *
		 * @return True if any adjustment done.
		 */
		boolean applyGroupStartDependency() {
			return applyStartDependency(a -> a instanceof Task);
		}

		/**
		 * Adjust start based on the dependencies. (For internal use only)
		 *
		 * @return True if any adjustment done.
		 */
		boolean applyStartDependency() {
			return applyStartDependency(a -> false);
		}

		private boolean applyStartDependency(final Predicate<ProjectTask> skip) {
			if (predecessors.isEmpty()) {
				return false;
			}
			for (final ProjectTask p : predecessors) {
				if (skip.test(p)) {
					continue;
				}
				p.applyStartDependency(skip);
			}
			boolean adjusted = false;
			LocalDateTime end;
			for (final ProjectTask p : predecessors) {
				if (skip.test(p)) {
					continue;
				}
				end = p.getEnd();
				if (!(p instanceof Task) || !((Task) p).isMilestone()) {
					end = end.plus(1, getDurationType());
				}
				if (start.isBefore(end)) {
					adjusted = true;
					start = end;
				}
			}
			return adjusted;
		}

		/**
		 * Start used by renderers may be different from the normal start. (For internal use only).
		 *
		 * @return Start for the renderers.
		 */
		@Override
		public LocalDateTime renderStart() {
			if (getDuration() == 0) {
				return start.minus(1, getDurationType());
			}
			return start;
		}
	}

	/**
	 * Represents a group of {@link Task}s.
	 *
	 * @author Syam
	 */
	public class TaskGroup extends ProjectTask {

		private final List<Task> tasks = new ArrayList<>();

		/**
		 * Constructor.
		 *
		 * @param name
		 *            Name.
		 */
		TaskGroup(final String name) {
			super(name);
		}

		/**
		 * Create and add a {@link Task} to this task group.
		 *
		 * @param name
		 *            Name of the task.
		 * @param duration
		 *            Duration of the task. (A duration of zero denotes a project milestone).
		 * @return Task created.
		 */
		public Task createTask(final String name, final int duration) {
			return Project.this.createTask(this, name, duration);
		}

		@Override
		public Color getColor() {
			Color color = super.getColor();
			if (color == null) {
				setColor(color = SOChart.getDefaultColor(taskGroups.indexOf(this)));
			}
			return color;
		}

		private void sort() {
			tasks.sort(Project::compare);
		}

		@Override
		public final int getDuration() {
			LocalDateTime start = null, end = null;
			LocalDateTime taskStart, taskEnd;
			for (final Task task : tasks) {
				taskStart = task.getStart();
				if (start == null || taskStart.isBefore(start)) {
					start = taskStart;
				}
				taskEnd = task.getEnd();
				if (end == null || taskEnd.isAfter(end)) {
					end = taskEnd;
				}
			}
			if (start == null || end == null) {
				return 0;
			}
			return (int) getDurationType().between(start, end);
		}

		@Override
		public final LocalDateTime getStart() {
			LocalDateTime taskStart = tasks.get(0).getStart();
			if (taskStart.isBefore(start)) {
				start = taskStart;
			}
			for (final Task task : tasks) {
				taskStart = task.getStart();
				if (taskStart.isBefore(start)) {
					start = taskStart;
				}
			}
			return super.getStart();
		}

		/**
		 * Get a {@link Task} from this group.
		 *
		 * @param taskIndex
		 *            Index of the task to be retrieved.
		 * @return {@link Task}.
		 */
		public final Task getTask(final int taskIndex) {
			return tasks.get(taskIndex);
		}

		/**
		 * Get the number of {@link Task} belonging to this group.
		 *
		 * @return Task count.
		 */
		public final int getTaskCount() {
			return tasks.size();
		}

		@Override
		public boolean isCompleted() {
			return tasks.stream().allMatch(ProjectTask::isCompleted);
		}

		@Override
		public double getCompleted() {
			return 0;
		}
	}

	/**
	 * Represents a task in a project.
	 *
	 * @author Syam
	 */
	public class Task extends ProjectTask {

		private final int duration;
		private final TaskGroup group;
		private double completed = 0;

		/**
		 * Constructor.
		 *
		 * @param taskGroup
		 *            Task group to which this task belongs to.
		 * @param name
		 *            Name.
		 * @param duration
		 *            Duration of the task. (A duration of zero denotes a project milestone).
		 */
		Task(final TaskGroup taskGroup, final String name, final int duration) {
			super(name);
			group = taskGroup;
			group.tasks.add(this);
			this.duration = Math.max(duration, 0);
		}

		/**
		 * Set completion percentage.
		 *
		 * @param completed
		 *            Percentage completed.
		 */
		public void setCompleted(final double completed) {
			this.completed = Math.min(Math.max(0, completed), 100);
		}

		@Override
		public final double getCompleted() {
			return completed;
		}

		@Override
		public final int getDuration() {
			return duration;
		}

		@Override
		public final boolean isMilestone() {
			return duration == 0;
		}

		@Override
		public LocalDateTime getStart() {
			start = max(start, group.earliestStart);
			return super.getStart();
		}

		/**
		 * Get the {@link TaskGroup} this task belongs to.
		 *
		 * @return {@link TaskGroup}.
		 */
		public final TaskGroup getGroup() {
			return group;
		}

		@Override
		public boolean isCompleted() {
			if (duration > 0) {
				return completed >= 100;
			}
			return predecessors.stream().allMatch(ProjectTask::isCompleted);
		}

		@Override
		public Color getColor() {
			final Color color = super.getColor();
			return color == null ? group.getColor() : color;
		}
	}

	/**
	 * Get all task groups of this project.
	 * <p>
	 * Note: Null will be returned if the project contains inconsistencies.
	 * </p>
	 *
	 * @return Stream of task groups.
	 */
	public Stream<TaskGroup> streamGroups() {
		try {
			validateConstraints();
		} catch (final ChartException e) {
			return null;
		}
		return taskGroups.stream();
	}

	/**
	 * Get all tasks in the given task group.
	 * <p>
	 * Note: Null will be returned if the project contains inconsistencies.
	 * </p>
	 *
	 * @param taskGroup
	 *            Task group.
	 * @return Stream of tasks.
	 */
	public Stream<Task> streamTasks(final TaskGroup taskGroup) {
		try {
			validateConstraints();
		} catch (final ChartException e) {
			return null;
		}
		return taskGroup.tasks.stream();
	}

	/**
	 * Get all dependencies of the given task or group.
	 * <p>
	 * Note: Null will be returned if the project contains inconsistencies.
	 * </p>
	 *
	 * @param task
	 *            Task.
	 * @return Stream of tasks/groups.
	 */
	public Stream<ProjectTask> streamDependencies(final ProjectTask task) {
		try {
			validateConstraints();
		} catch (final ChartException e) {
			return null;
		}
		return task.predecessors.stream();
	}

	@Override
	<T> Iterator<T> iterator(final BiFunction<AbstractTask, Integer, T> encoder,
			final Predicate<AbstractTask> taskFilter) {
		return new TaskIterator<>(encoder, taskFilter);
	}

	private class TaskIterator<T> implements Iterator<T> {

		private int index = -1;
		private int groupIndex = -1;
		private int taskIndex = -1;
		private Task next = null;
		private final BiFunction<AbstractTask, Integer, T> encoder;
		private final Predicate<AbstractTask> taskFilter;

		private TaskIterator(final BiFunction<AbstractTask, Integer, T> encoder,
				final Predicate<AbstractTask> taskFilter) {
			this.encoder = encoder;
			this.taskFilter = taskFilter;
		}

		@Override
		public boolean hasNext() {
			if (next != null) {
				return true;
			}
			if (groupIndex == Integer.MIN_VALUE) {
				return false;
			}
			if (groupIndex == -1) {
				if (taskGroups.isEmpty()) {
					groupIndex = Integer.MIN_VALUE;
					return false;
				}
				groupIndex = 0;
				taskIndex = 0;
			} else {
				++taskIndex;
			}
			TaskGroup taskGroup = taskGroups.get(groupIndex);
			while (taskIndex >= taskGroup.tasks.size()) {
				if (++groupIndex >= taskGroups.size()) {
					groupIndex = Integer.MIN_VALUE;
					return false;
				}
				taskGroup = taskGroups.get(groupIndex);
				if (taskGroup.tasks.isEmpty()) {
					continue;
				}
				taskIndex = 0;
				break;
			}
			++index;
			next = taskGroup.getTask(taskIndex);
			if (taskFilter != null && !taskFilter.test(next)) {
				next = null;
				return hasNext();
			}
			return true;
		}

		@Override
		public T next() {
			if (next == null) {
				throw new NoSuchElementException();
			}
			final Task task = next;
			next = null;
			return encoder.apply(task, index);
		}
	}

	@Override
	protected AbstractDataProvider<String> dependencies() {
		final BiFunction<AbstractTask, Integer, String> func = (t, i) -> "[" + i + "," + encode(t.renderStart()) + ","
				+ encode(t.getEnd()) + "," + dependents((Task) t) + "]";
		return dataProvider(DataType.OBJECT, func, t -> ((Task) t).predecessors.size() > 0);
	}

	private String dependents(final Task task) {
		final StringBuilder sb = new StringBuilder("{\"d\":[");
		boolean first = true;
		for (ProjectTask at : task.predecessors) {
			if (at instanceof TaskGroup) {
				final TaskGroup tg = (TaskGroup) at;
				at = tg.tasks.get(tg.tasks.size() - 1);
			}
			if (first) {
				first = false;
			} else {
				sb.append(",");
			}
			sb.append("[").append(indexOf((Task) at)).append(",").append(encode(at.renderStart())).append(",")
					.append(encode(at.getEnd())).append("]");
		}
		sb.append("]}");
		return "\"" + sb.toString().replace("\"", "^") + "\"";
	}

	private int indexOf(final Task t) {
		int n = 0;
		for (final TaskGroup tg : taskGroups) {
			if (tg == t.group) {
				break;
			}
			n += tg.tasks.size();
		}
		return n + t.group.tasks.indexOf(t);
	}

	/**
	 * Get the extra axis label for the task. (Will be used for displaying the 2nd line of the axis label).
	 * <p>
	 * The default implementation returns the description of the time-left to work on the task.
	 * </p>
	 *
	 * @param task
	 *            Task.
	 * @return Label.
	 */
	@Override
	protected String getExtraAxisLabel(final AbstractTask task) {
		if (task.isCompleted()) {
			return task.isMilestone() ? "Achieved" : "Completed";
		}
		final LocalDateTime today = getToday();
		final ChronoUnit durationType = getDurationType();
		final Duration duration = Duration.between(task.getEnd(), today);
		String timeName;
		long left;
		if (durationType.isDateBased()) {
			left = duration.toDays();
			timeName = "day";
		} else {
			switch (durationType) {
			case SECONDS: {
				left = duration.toSeconds();
				timeName = "second";
				break;
			}
			case MINUTES: {
				left = duration.toMinutes();
				timeName = "minute";
				break;
			}
			case HOURS: {
				left = duration.toHours();
				timeName = "hour";
				break;
			}
			default: {
				left = duration.toMillis();
				timeName = "millisecond";
				break;
			}
			}
		}
		if (Math.abs(left) != 1) {
			timeName += "s";
		}
		if (left > 0) {
			return "Late by " + left + " " + timeName;
		}
		return (-left) + " " + timeName + " remaining";
	}

	@Override
	final String getAxisLabel(final AbstractTask abstractTask, final int index) {
		final Task task = (Task) abstractTask;
		return "[" + index + ",\"" + getAxisLabel(task.group) + "\","
				+ (task == task.group.tasks.get(task.group.tasks.size() - 1) ? 0 : 1) + ",\"" + getAxisLabel(task)
				+ "\",\"" + getExtraAxisLabel(task) + "\"," + task.getColor() + "," + groupFontSize(task) + ","
				+ extraFontSize(task) + "]";
	}

	@Override
	AbstractDataProvider<String> axisLabels() {
		return dataProvider(DataType.OBJECT, this::getAxisLabel);
	}

	@Override
	protected String getTooltipLabel(final AbstractTask abstractTask) {
		final Task task = (Task) abstractTask;
		String extra = task.getExtraInfo();
		if (extra == null || extra.isEmpty()) {
			extra = "";
		} else {
			extra = "<br>" + extra;
		}
		final Function<LocalDateTime, String> timeConverter = getTooltipTimeFormat();
		final String s = getLabel(task) + "<br>" + timeConverter.apply(task.start);
		if (task.isMilestone()) {
			return s + extra;
		}
		return s + " - " + timeConverter.apply(task.getEnd()) + " (" + task.getDuration() + ")" + extra;
	}
}
