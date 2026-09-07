export type Role = "ADMIN" | "TEACHER" | "STUDENT";
export interface User {
  userId: string;
  account: string;
  name: string;
  role: Role;
  homePath: string;
}
export interface Semester {
  id: string;
  academicYear: number;
  season: "AUTUMN" | "SPRING";
  startsOn: string;
  endsOnInclusive: string;
  endsOnExclusive: string;
  status: "UPCOMING" | "ACTIVE" | "ENDED";
}
export interface Meeting {
  weekday: number;
  startTime: string;
  endTime: string;
}
export interface Course {
  id: string;
  code: string;
  name: string;
  description: string;
  semester: Semester;
  publication: "DRAFT" | "PUBLISHED" | "UNPUBLISHED";
  version: string;
  meetings: Meeting[];
  displayColor: string;
  borderStyle: string;
  teacher: { id: string; name: string };
  selectedCount?: number;
  selected?: boolean;
  allowedActions: string[];
}
export interface Enrollment {
  id: string;
  generation: string;
  version: string;
  enrolledAt: string;
  semesterId: string;
  course: Course;
}
export interface Account {
  id: string;
  account: string;
  name: string;
  role: "TEACHER" | "STUDENT";
  registrationNumber: string;
  email: string;
  version: string;
  createdAt: string;
}
export interface Student {
  studentId: string;
  studentAccount: string;
  studentName: string;
  selectedCount: number;
  courses: Course[];
}
export interface RosterItem {
  studentId: string;
  studentAccount: string;
  studentName: string;
  accountDeleted: boolean;
  enrolledAt: string;
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface TimetableData {
  semester: Semester;
  courses: Course[];
  snapshotAt: string;
}
export const roleNames: Record<Role, string> = {
  ADMIN: "管理员",
  TEACHER: "教师",
  STUDENT: "学生",
};
export const publicationNames = {
  DRAFT: "未上架",
  PUBLISHED: "已上架",
  UNPUBLISHED: "已下架",
};
export const semesterNames = {
  UPCOMING: "未开始",
  ACTIVE: "进行中",
  ENDED: "已结束",
};
export const weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五"];
export function semesterLabel(s: Semester) {
  return `${s.academicYear}—${s.academicYear + 1} 学年${s.season === "AUTUMN" ? "秋季" : "春季"}`;
}
export function dateTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
