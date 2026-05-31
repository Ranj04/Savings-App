export function possessive(name) {
  if (!name) return '';
  const n = String(name);
  return n.endsWith('s') || n.endsWith('S') ? `${n}'` : `${n}'s`;
}
