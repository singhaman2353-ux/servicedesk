import { ApiError } from './api.js';

export function minutesLabel(seconds) {
  if (seconds == null) return 'a few minutes';
  const minutes = Math.max(1, Math.ceil(seconds / 60));
  return `${minutes} minute${minutes === 1 ? '' : 's'}`;
}

/**
 * Turns any error into { form, fields } for a form to display.
 *   form   -> one message for the banner above the submit button (or null)
 *   fields -> { fieldName: message } shown under the matching inputs
 * knownFields lists the inputs the form actually has; any other field error goes to the banner.
 */
export function toFormErrors(error, { context, knownFields = [] }) {
  const result = { form: null, fields: {} };

  if (!(error instanceof ApiError)) {
    result.form = 'Something went wrong. Please try again.';
    return result;
  }

  switch (true) {
    case error.status === 0:
      result.form = error.message;
      break;
    case error.status === 400: {
      const unmatched = [];
      for (const item of error.fieldErrors) {
        if (knownFields.includes(item.field)) {
          result.fields[item.field] ??= item.message;
        } else {
          unmatched.push(item.message);
        }
      }
      if (unmatched.length) result.form = unmatched.join(' ');
      else if (!Object.keys(result.fields).length) result.form = error.message;
      break;
    }
    case error.status === 401:
      result.form = context === 'login'
        ? 'Invalid email or password'
        : 'Your session has expired. Please sign in again.';
      break;
    case error.status === 403:
      result.form = 'You do not have permission to do that.';
      break;
    case error.status === 409 && context === 'register':
      result.fields.email = 'This email is already registered. Try signing in instead.';
      break;
    case error.status === 429:
      result.form = `Too many attempts, try again in ${minutesLabel(error.retryAfterSeconds)}.`;
      break;
    default:
      result.form = error.message;
  }
  return result;
}
