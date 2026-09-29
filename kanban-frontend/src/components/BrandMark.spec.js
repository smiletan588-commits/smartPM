// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BrandMark from './BrandMark.vue'

describe('SmartPM brand', () => {
  it('uses the shared SVG and exposes a single accessible brand name', () => {
    const wrapper = mount(BrandMark)
    expect(wrapper.attributes('aria-label')).toBe('SmartPM')
    expect(wrapper.get('.brand-symbol').attributes('src')).toBe(`${import.meta.env.BASE_URL}smartpm-icon.svg`)
    expect(wrapper.get('.brand-symbol').attributes('alt')).toBe('')
    expect(wrapper.get('.brand-name').text()).toBe('SmartPM')
  })

  it('keeps an accessible icon without the wordmark when collapsed', () => {
    const wrapper = mount(BrandMark, { props: { compact: true } })
    expect(wrapper.classes()).toContain('compact')
    expect(wrapper.find('.brand-name').exists()).toBe(false)
    expect(wrapper.find('.brand-symbol').exists()).toBe(true)
    expect(wrapper.attributes('aria-label')).toBe('SmartPM')
  })
})
