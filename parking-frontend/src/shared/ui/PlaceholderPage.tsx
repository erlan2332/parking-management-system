import { Construction } from 'lucide-react'
import { Card, PageHeader } from './Surface'

export function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="grid gap-6">
      <PageHeader title={title} />
      <Card><div className="state-panel"><Construction className="size-7 text-blue-600" /><span>Раздел подключается к Backend.</span></div></Card>
    </div>
  )
}
