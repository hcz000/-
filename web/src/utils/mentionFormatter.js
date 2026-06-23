const MENTION_PATTERN = /@[^\s@，。！？、；：,.!?;:()[\]{}"']{1,32}/gu
const EMAIL_LOCAL_CHAR_PATTERN = /[A-Za-z0-9._%+-]/

const escapeHtml = (value) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

export const highlightMentions = (value, fallback = '') => {
  const text = value == null || value === '' ? fallback : value
  const source = String(text)
  let cursor = 0
  let output = ''

  for (const match of source.matchAll(MENTION_PATTERN)) {
    const [mention] = match
    const matchIndex = match.index ?? 0
    const previousChar = matchIndex > 0 ? source.charAt(matchIndex - 1) : ''

    if (EMAIL_LOCAL_CHAR_PATTERN.test(previousChar)) {
      continue
    }

    output += escapeHtml(source.slice(cursor, matchIndex))
    output += `<span class="mention-highlight">${escapeHtml(mention)}</span>`
    cursor = matchIndex + mention.length
  }

  output += escapeHtml(source.slice(cursor))
  return output
}
