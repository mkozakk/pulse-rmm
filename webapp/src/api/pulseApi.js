import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import { clearCredentials, setCredentials } from '../store/authSlice'

const rawBaseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api',
  credentials: 'include',
  prepareHeaders: (headers, { getState }) => {
    const token = getState().auth.token
    if (token) headers.set('Authorization', `Bearer ${token}`)
    return headers
  }
})

export function makeBaseQueryWithRefresh(rawQuery) {
  return async function baseQueryWithRefresh(args, api, extraOptions) {
    const result = await rawQuery(args, api, extraOptions)
    if (result?.error?.status !== 401) return result

    const url = typeof args === 'string' ? args : args.url
    if (url === '/auth/refresh' || url === '/auth/login' || url === '/auth/register') {
      if (url === '/auth/refresh') api.dispatch(clearCredentials())
      return result
    }

    const refreshResult = await rawQuery({ url: '/auth/refresh', method: 'POST' }, api, extraOptions)
    if (refreshResult?.data?.accessToken) {
      api.dispatch(setCredentials(refreshResult.data.accessToken))
      return rawQuery(args, api, extraOptions)
    }

    api.dispatch(clearCredentials())
    return result
  }
}

const baseQueryWithRefresh = makeBaseQueryWithRefresh(rawBaseQuery)

export const pulseApi = createApi({
  reducerPath: 'pulseApi',
  baseQuery: baseQueryWithRefresh,
  endpoints: (builder) => ({
    register: builder.mutation({
      query: (body) => ({ url: '/auth/register', method: 'POST', body })
    }),
    login: builder.mutation({
      query: (body) => ({ url: '/auth/login', method: 'POST', body })
    }),
    refresh: builder.mutation({
      query: () => ({ url: '/auth/refresh', method: 'POST' })
    }),
    logout: builder.mutation({
      query: () => ({ url: '/auth/logout', method: 'POST' })
    }),
    getGroups: builder.query({
      query: () => '/groups',
      keepUnusedDataFor: 0
    }),
    createGroup: builder.mutation({
      query: (body) => ({ url: '/groups', method: 'POST', body })
    }),
    getTagRules: builder.query({
      query: () => '/tag-rules',
      keepUnusedDataFor: 0
    }),
    createTagRule: builder.mutation({
      query: (body) => ({ url: '/tag-rules', method: 'POST', body })
    }),
    evaluateTagRules: builder.mutation({
      query: () => ({ url: '/tag-rules/evaluate', method: 'POST' })
    }),
    createEnrolmentToken: builder.mutation({
      query: (body) => ({ url: '/enrolment/tokens', method: 'POST', body })
    }),
    getEndpoints: builder.query({
      query: () => '/endpoints',
      keepUnusedDataFor: 0
    }),
    updateEndpointGroup: builder.mutation({
      query: ({ id, groupId }) => ({
        url: `/endpoints/${id}/group`,
        method: 'PUT',
        body: { groupId }
      })
    }),
    updateEndpointTags: builder.mutation({
      query: ({ id, tags }) => ({
        url: `/endpoints/${id}/tags`,
        method: 'PUT',
        body: { tags }
      })
    }),
    getScripts: builder.query({
      query: ({ status = 'all', page = 0, size = 50 } = {}) =>
        `/scripts?status=${status}&page=${page}&size=${size}`,
      keepUnusedDataFor: 0
    }),
    getScript: builder.query({
      query: (id) => `/scripts/${id}`,
      keepUnusedDataFor: 0
    }),
    createScript: builder.mutation({
      query: (body) => ({ url: '/scripts', method: 'POST', body })
    }),
    approveScript: builder.mutation({
      query: (id) => ({ url: `/scripts/${id}/approve`, method: 'POST' })
    }),
    runScript: builder.mutation({
      query: ({ id, ...body }) => ({ url: `/scripts/${id}/run`, method: 'POST', body })
    }),
    getScriptRunResults: builder.query({
      query: (runId) => `/scripts/runs/${runId}/results`,
      keepUnusedDataFor: 0
    }),
    ackScriptExecution: builder.mutation({
      query: ({ runId, endpointId, ...body }) => ({
        url: `/scripts/runs/${runId}/endpoints/${endpointId}/ack`,
        method: 'POST',
        body
      })
    }),
    getSoftware: builder.query({
      query: (endpointId) => `/endpoints/${endpointId}/software`,
      keepUnusedDataFor: 0
    }),
    installSoftware: builder.mutation({
      query: ({ endpointId, ...body }) => ({
        url: `/endpoints/${endpointId}/software/install`,
        method: 'POST',
        body
      })
    }),
    updateSoftware: builder.mutation({
      query: ({ endpointId, ...body }) => ({
        url: `/endpoints/${endpointId}/software/update`,
        method: 'POST',
        body
      })
    }),
    removeSoftware: builder.mutation({
      query: ({ endpointId, ...body }) => ({
        url: `/endpoints/${endpointId}/software/remove`,
        method: 'POST',
        body
      })
    }),
    getEndpoint: builder.query({
      query: (id) => `/endpoints/${id}`,
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
    }),
    getAlertRules: builder.query({
      query: () => '/alert-rules',
      keepUnusedDataFor: 0
    }),
    createAlertRule: builder.mutation({
      query: (body) => ({ url: '/alert-rules', method: 'POST', body })
    }),
    deleteAlertRule: builder.mutation({
      query: (id) => ({ url: `/alert-rules/${id}`, method: 'DELETE' })
    }),
    getAlerts: builder.query({
      query: (status = 'open') => `/alerts?status=${status}`,
      keepUnusedDataFor: 0
    }),
    ackAlert: builder.mutation({
      query: (id) => ({ url: `/alerts/${id}/ack`, method: 'POST' })
    }),
    getAuditLog: builder.query({
      query: ({ user, endpoint, from, to, page = 0, size = 50 } = {}) => {
        const params = new URLSearchParams()
        if (user) params.set('user', user)
        if (endpoint) params.set('endpoint', endpoint)
        if (from) params.set('from', from)
        if (to) params.set('to', to)
        params.set('page', page)
        params.set('size', size)
        return `/audit?${params}`
      },
      keepUnusedDataFor: 0
    })
  })
})

export const {
  useRegisterMutation,
  useLoginMutation,
  useRefreshMutation,
  useLogoutMutation,
  useGetGroupsQuery,
  useCreateGroupMutation,
  useGetTagRulesQuery,
  useCreateTagRuleMutation,
  useEvaluateTagRulesMutation,
  useCreateEnrolmentTokenMutation,
  useGetEndpointsQuery,
  useUpdateEndpointGroupMutation,
  useUpdateEndpointTagsMutation,
  useGetScriptsQuery,
  useGetScriptQuery,
  useCreateScriptMutation,
  useApproveScriptMutation,
  useRunScriptMutation,
  useGetScriptRunResultsQuery,
  useAckScriptExecutionMutation,
  useGetSoftwareQuery,
  useInstallSoftwareMutation,
  useUpdateSoftwareMutation,
  useRemoveSoftwareMutation,
  useGetEndpointQuery,
  useGetMetricsQuery,
  useCreateSessionMutation,
  useGetSessionQuery,
  useEndSessionMutation,
  useGetAlertRulesQuery,
  useCreateAlertRuleMutation,
  useDeleteAlertRuleMutation,
  useGetAlertsQuery,
  useAckAlertMutation,
  useGetAuditLogQuery
} = pulseApi
