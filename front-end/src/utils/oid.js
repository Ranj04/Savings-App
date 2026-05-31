export const isHex24 = (s) => typeof s === "string" && /^[0-9a-fA-F]{24}$/.test(s);
export const oid = (v) => {
  if (!v) return null;
  if (typeof v === "string" && isHex24(v)) return v;
  if (typeof v === "object") {
    if (isHex24(v.$oid)) return v.$oid;
    if (isHex24(v._id))  return v._id;
    if (v._id && isHex24(v._id.$oid)) return v._id.$oid;
    if (isHex24(v.id)) return v.id;
  }
  return null;
};
export const num = (v, d=0) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : d;
};

// Lenient id extractor that tolerates various backend shapes (id, _id.$oid, _id, goalId)
// and falls back to '' when nothing matches. Use this when you just need a key/string id;
// use oid() when you specifically require a valid 24-hex ObjectId.
export const idOf = (obj) => {
  if (!obj) return '';
  if (typeof obj === 'string') return obj;
  if (obj.id) return obj.id;
  if (obj._id) {
    if (typeof obj._id === 'string') return obj._id;
    if (obj._id.$oid) return obj._id.$oid;
  }
  if (obj.goalId) return obj.goalId;
  return '';
};

// Format a number as a fixed 2-decimal, locale-aware string.
export const fmt = (v) => num(v, 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
