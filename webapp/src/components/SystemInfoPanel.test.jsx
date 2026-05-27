import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import SystemInfoPanel from './SystemInfoPanel'

describe('SystemInfoPanel', () => {
  it('shows empty state when info is missing', () => {
    render(<SystemInfoPanel info={null} />)
    expect(screen.getByText(/no system info reported yet/i)).toBeInTheDocument()
  })

  it('renders cpu model, disk count and nic count', () => {
    const info = {
      cpuModel: 'Test CPU v9',
      cpuPhysical: 4,
      cpuLogical: 8,
      cpuFreqMhz: 3200,
      ramTotal: 16 * 1024 * 1024 * 1024,
      swapTotal: 2 * 1024 * 1024 * 1024,
      disks: [
        { device: '/dev/sda1', mountpoint: '/', fstype: 'ext4', totalBytes: 500 * 1024 * 1024 * 1024 }
      ],
      nics: [
        { name: 'eth0', mac: 'aa:bb:cc:dd:ee:ff', addresses: ['192.168.1.10/24'], mtu: 1500 }
      ]
    }

    render(<SystemInfoPanel info={info} />)

    expect(screen.getByText('Test CPU v9')).toBeInTheDocument()
    expect(screen.getByText(/4 physical \/ 8 logical/i)).toBeInTheDocument()
    expect(screen.getByText('/dev/sda1')).toBeInTheDocument()
    expect(screen.getByText('eth0')).toBeInTheDocument()
    expect(screen.getByText(/192\.168\.1\.10/)).toBeInTheDocument()
  })
})
