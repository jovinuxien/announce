/**
 * The order module and messaging answer a command from the aggregate while their read models catch up a moment
 * later, so a screen that refetches a row right after a command (or right after another screen created it) may
 * still see the old row, or none. Screens refetch once more after this delay, and retry a missing row quietly.
 */
export const ROW_SETTLE_MS = 1000;
/** Quiet retries of a row that should exist but is not projected yet, before the alert shows. */
export const ROW_RETRIES = 8;
