import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import FileTransferPanel from './FileTransferPanel'

describe('FileTransferPanel', () => {
  it('renders drop zone', () => {
    render(<FileTransferPanel sendFile={vi.fn()} requestDownload={vi.fn()} />)
    expect(screen.getByText(/drop a file here/i)).toBeInTheDocument()
  })

  it('renders download input', () => {
    render(<FileTransferPanel sendFile={vi.fn()} requestDownload={vi.fn()} />)
    expect(screen.getByPlaceholderText(/path to download/i)).toBeInTheDocument()
  })

  it('shows uploading name after drop', () => {
    const sendFile = vi.fn()
    render(<FileTransferPanel sendFile={sendFile} requestDownload={vi.fn()} />)

    const dropZone = screen.getByText(/drop a file here/i).closest('div')
    const file = new File(['hello'], 'report.txt', { type: 'text/plain' })
    fireEvent.drop(dropZone, { dataTransfer: { files: [file] } })

    expect(screen.getByText(/uploading report.txt/i)).toBeInTheDocument()
    expect(sendFile).toHaveBeenCalledWith(file)
  })
})
