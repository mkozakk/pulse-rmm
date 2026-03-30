import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import NetworkChart from './NetworkChart'

describe('NetworkChart', () => {
  it('shows empty message when fewer than two samples', () => {
    render(<NetworkChart rxSamples={[{ sampledAt: '2026-01-01T00:00:00Z', value: 0 }]}
                         txSamples={[{ sampledAt: '2026-01-01T00:00:00Z', value: 0 }]}
                         nic="eth0" />)
    expect(screen.getByText(/need at least two samples/i)).toBeInTheDocument()
  })

  it('renders the chart heading when enough samples are present', () => {
    const rx = [
      { sampledAt: '2026-01-01T00:00:00Z', value: 0 },
      { sampledAt: '2026-01-01T00:00:30Z', value: 30000 }
    ]
    const tx = [
      { sampledAt: '2026-01-01T00:00:00Z', value: 0 },
      { sampledAt: '2026-01-01T00:00:30Z', value: 6000 }
    ]
    render(<NetworkChart rxSamples={rx} txSamples={tx} nic="eth0" />)
    expect(screen.getByText(/network \(eth0\)/i)).toBeInTheDocument()
  })
})
