import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import Chat from './Chat.vue';

describe('Chat.vue', () => {
  it('renders the chat interface', () => {
    const wrapper = mount(Chat);
    expect(wrapper.find('.chat-container').exists()).toBe(true);
    expect(wrapper.find('input').exists()).toBe(true);
    expect(wrapper.find('button').exists()).toBe(true);
  });

  it('adds a new message when the user sends one', async () => {
    const wrapper = mount(Chat);
    const input = wrapper.find('input');
    await input.setValue('Hello, world!');
    await wrapper.find('button').trigger('click');
    expect(wrapper.findAll('.message').length).toBe(2); // User message and bot's empty response
    expect(wrapper.html()).toContain('Hello, world!');
  });
});