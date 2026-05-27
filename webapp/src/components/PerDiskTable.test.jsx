import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import PerDiskTable from './PerDiskTable'

describe('PerDiskTable', () => {
  it('renders a row per disk and computes usage percent', () => {
    const disks = [
      { device: '/dev/sda1', mountpoint: '/', fstype: 'ext4', totalBytes: 1000 },
      { device: '/dev/sdb1', mountpoint: '/data', fstype: 'xfs', totalBytes: 2000 }
    ]
    const samples = [
      { type: 'disk.used_bytes', value: 250, labels: { mount: '/' } },
      { type: 'disk.free_bytes', value: 750, labels: { mount: '/' } },
      { type: 'disk.total_bytes', value: 1000, labels: { mount: '/' } }
    ]

    render(<PerDiskTable disks={disks} samples={samples} />)

    expect(screen.getByText('/dev/sda1')).toBeInTheDocument()
    expect(screen.getByText('/dev/sdb1')).toBeInTheDocument()
    expect(screen.getByText('25%')).toBeInTheDocument()
  })

  it('shows empty message when no disks given', () => {
    render(<PerDiskTable disks={[]} samples={[]} />)
    expect(screen.getByText(/no disks reported/i)).toBeInTheDocument()
  })
})
