import { idOf } from '../utils/oid';
import { api } from './client';

export async function listGoals() {
  try {
    const res = await api('/goals/list');
    
    if (!res.ok) {
      console.error('Failed to load goals:', res.status);
      return [];
    }
    
    const data = await res.json();
    
    if (data.success === false) {
      console.error('Failed to load goals:', data.message);
      return [];
    }
    
    const rawContainer = data?.data != null ? data.data : data; // unwrap one level
    const raw = Array.isArray(rawContainer) ? rawContainer : (Array.isArray(rawContainer?.data) ? rawContainer.data : []);
    
    return raw.map(g => {
      const name = g.name || g.goal?.name || (typeof g.goal === 'string' ? g.goal : '');
      return {
        ...g,
        name, // flatten
        _id: idOf(g._id),
        id: idOf(g._id || g.id || g),
        accountId: idOf(g.accountId || g.account?._id || g.account),
        allocatedAmount: Number(g.allocatedAmount ?? 0),
        targetAmount: g.targetAmount != null && g.targetAmount !== '' ? Number(g.targetAmount) : null,
      };
    });
  } catch (error) {
    console.error('Error loading goals:', error);
    return [];
  }
}
