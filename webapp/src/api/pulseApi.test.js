import { describe, it, expect, vi } from 'vitest'
import { makeBaseQueryWithRefresh } from './pulseApi'

describe('pulseApi auth refresh', () => {
  it('retries once after a 401 from protected endpoint', async () => {
    const dispatch = vi.fn()
    const rawQuery = vi.fn()
      .mockResolvedValueOnce({ error: { status: 401 } })
      .mockResolvedValueOnce({ data: { accessToken: 'fresh-token' } })
      .mockResolvedValueOnce({ data: [{ id: '1' }] })

    const baseQuery = makeBaseQueryWithRefresh(rawQuery)
    const result = await baseQuery({ url: '/endpoints' }, { dispatch }, {})

    expect(result.data).toEqual([{ id: '1' }])
    expect(rawQuery).toHaveBeenCalledTimes(3)
    expect(dispatch).toHaveBeenCalled()
  })
})
