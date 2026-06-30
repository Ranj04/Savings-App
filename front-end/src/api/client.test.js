import { api } from './client';

describe('api() client', () => {
  let fetchMock;
  beforeEach(() => {
    fetchMock = jest.fn().mockResolvedValue({ ok: true });
    global.fetch = fetchMock;
  });
  afterEach(() => { jest.restoreAllMocks(); });

  test.each(['Content-Type', 'content-type'])(
    'JSON-stringifies an object body when header is "%s"',
    async (headerKey) => {
      await api('/goals/create', {
        method: 'POST',
        headers: { [headerKey]: 'application/json' },
        body: { accountName: 'Vacation', goalName: 'Japan' },
      });
      const [, opts] = fetchMock.mock.calls[0];
      expect(typeof opts.body).toBe('string');
      expect(JSON.parse(opts.body)).toEqual({ accountName: 'Vacation', goalName: 'Japan' });
    }
  );

  test('leaves an already-stringified body untouched', async () => {
    const raw = JSON.stringify({ a: 1 });
    await api('/x', { headers: { 'Content-Type': 'application/json' }, body: raw });
    expect(fetchMock.mock.calls[0][1].body).toBe(raw);
  });

  test('always sends credentials and normalizes a relative path', async () => {
    await api('health');
    const [url, opts] = fetchMock.mock.calls[0];
    expect(url).toBe('/health');
    expect(opts.credentials).toBe('include');
  });
});
