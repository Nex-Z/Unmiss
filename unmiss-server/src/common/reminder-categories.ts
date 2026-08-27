export const REMINDER_CATEGORIES = [
  'work',
  'life',
  'finance',
  'health',
  'social',
  'entertainment',
  'other',
] as const

export type ReminderCategory = (typeof REMINDER_CATEGORIES)[number]
export type CategoryWeights = Record<ReminderCategory, number>

export const DEFAULT_CATEGORY_WEIGHTS: CategoryWeights = {
  work: 3,
  life: 3,
  finance: 3,
  health: 3,
  social: 3,
  entertainment: 3,
  other: 3,
}
