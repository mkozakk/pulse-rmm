import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'

export const pulseApi = createApi({
  reducerPath: 'pulseApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/api',
    credentials: 'include',
    prepareHeaders: (headers, { getState }) => {
      const token = getState().auth.token
      if (token) headers.set('Authorization', `Bearer ${token}`)
      return headers
    }
  }),
  endpoints: (builder) => ({
    login: builder.mutation({
      query: (body) => ({ url: '/auth/login', method: 'POST', body })
    }),
    refresh: builder.mutation({
      query: () => ({ url: '/auth/refresh', method: 'POST' })
    }),
    logout: builder.mutation({
      query: () => ({ url: '/auth/logout', method: 'POST' })
    }),
    getEndpoints: builder.query({
      query: () => '/endpoints',
      keepUnusedDataFor: 0
    }),
    getMetrics: builder.query({
      query: ({ id, from, to, type }) =>
        `/endpoints/${id}/metrics?from=${from}&to=${to}&type=${type}`,
      keepUnusedDataFor: 0
    }),
    createSession: builder.mutation({
      query: (body) => ({ url: '/sessions', method: 'POST', body })
    }),
    getSession: builder.query({
      query: (id) => `/sessions/${id}`,
      keepUnusedDataFor: 0
    }),
    endSession: builder.mutation({
      query: (id) => ({ url: `/sessions/${id}`, method: 'DELETE' })
    })
  })
})

export const {
  useLoginMutation,
  useRefreshMutation,
  useLogoutMutation,
  useGetEndpointsQuery,
  useGetMetricsQuery,
  useCreateSessionMutation,
  useGetSessionQuery,
  useEndSessionMutation
} = pulseApi
