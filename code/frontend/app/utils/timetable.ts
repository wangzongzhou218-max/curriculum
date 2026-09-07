import type { Course, Meeting } from "../types/domain";

export const minute = (value: string) =>
  Number(value.slice(0, 2)) * 60 + Number(value.slice(3));
export interface Block {
  course: Course;
  meeting: Meeting;
  top: number;
  height: number;
  column: number;
  columns: number;
}
/** Greedy interval partitioning; adjacent courses reuse a column, all overlaps remain visible. */
export function layoutDay(courses: Course[], day: number): Block[] {
  const entries = courses
    .flatMap((course) =>
      course.meetings
        .filter((m) => m.weekday === day)
        .map((meeting) => ({ course, meeting })),
    )
    .sort(
      (a, b) =>
        minute(a.meeting.startTime) - minute(b.meeting.startTime) ||
        minute(a.meeting.endTime) - minute(b.meeting.endTime) ||
        a.course.code.localeCompare(b.course.code, undefined, {
          numeric: true,
        }),
    );
  const blocks: Block[] = [];
  let group: Block[] = [],
    ends: number[] = [],
    groupEnd = -1;
  const finish = () => {
    group.forEach((block) => {
      block.columns = ends.length;
    });
    group = [];
    ends = [];
    groupEnd = -1;
  };
  for (const entry of entries) {
    const start = minute(entry.meeting.startTime),
      end = minute(entry.meeting.endTime);
    if (start >= groupEnd) finish();
    let column = ends.findIndex((previousEnd) => previousEnd <= start);
    if (column < 0) column = ends.length;
    ends[column] = end;
    groupEnd = Math.max(groupEnd, end);
    const block: Block = {
      ...entry,
      top: ((start - 480) / 30) * 40,
      height: ((end - start) / 30) * 40,
      column,
      columns: 1,
    };
    group.push(block);
    blocks.push(block);
  }
  finish();
  return blocks;
}
